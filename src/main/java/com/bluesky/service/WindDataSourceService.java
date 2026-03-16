package com.bluesky.service;

import com.bluesky.common.ResultCode;
import com.bluesky.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class WindDataSourceService {

    @Value("${wind.field.u-file:data/uwnd.nc}")
    private String uFilePath;

    @Value("${wind.field.v-file:data/vwnd.nc}")
    private String vFilePath;

    @Value("${wind.field.u-url:}")
    private String uFileUrl;

    @Value("${wind.field.v-url:}")
    private String vFileUrl;

    @Value("${wind.field.auto-download-if-missing:true}")
    private boolean autoDownloadIfMissing;

    @Value("${wind.field.auto-update-enabled:true}")
    private boolean autoUpdateEnabled;

    @Value("${wind.field.min-file-size-bytes:1024}")
    private long minFileSizeBytes;

    @Value("${wind.field.download-timeout-seconds:600}")
    private int downloadTimeoutSeconds;

    @Value("${wind.field.download-threads:2}")
    private int downloadThreads;

    @Value("${wind.field.download-progress-log-interval-seconds:5}")
    private int downloadProgressLogIntervalSeconds;

    @Value("${wind.field.download-buffer-size:1048576}")
    private int downloadBufferSize;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ConcurrentMap<String, CompletableFuture<Void>> inProgressDownloads = new ConcurrentHashMap<>();
    private ExecutorService downloadExecutor;

    @PostConstruct
    public void init() {
        int poolSize = Math.max(1, downloadThreads);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "wind-download-" + index.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        downloadExecutor = Executors.newFixedThreadPool(poolSize, factory);

        if (autoUpdateEnabled) {
            triggerAsyncUpdate(Path.of(uFilePath), uFileUrl, "u");
            triggerAsyncUpdate(Path.of(vFilePath), vFileUrl, "v");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (downloadExecutor != null) {
            downloadExecutor.shutdownNow();
        }
    }

    public WindSourceFiles ensureSourceFiles() {
        Path uPath = Path.of(uFilePath);
        Path vPath = Path.of(vFilePath);

        ensureFileReadyAsync(uPath, uFileUrl, "u");
        ensureFileReadyAsync(vPath, vFileUrl, "v");
        return new WindSourceFiles(uPath, vPath);
    }

    @Scheduled(fixedDelayString = "#{${wind.field.update-interval-seconds:7200} * 1000}")
    public void scheduledUpdateFromUrl() {
        if (!autoUpdateEnabled) {
            return;
        }
        triggerAsyncUpdate(Path.of(uFilePath), uFileUrl, "u");
        triggerAsyncUpdate(Path.of(vFilePath), vFileUrl, "v");
    }

    private void ensureFileReadyAsync(Path filePath, String fileUrl, String label) {
        if (isValidFile(filePath)) {
            return;
        }

        if (!autoDownloadIfMissing) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Wind source file missing: " + filePath);
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BusinessException(
                    ResultCode.SERVICE_UNAVAILABLE,
                    "Wind source file missing and " + label + "-url is not configured: " + filePath
            );
        }

        triggerAsyncUpdate(filePath, fileUrl, label);
        throw new BusinessException(
                ResultCode.SERVICE_UNAVAILABLE,
                "Wind source file is missing. Background download started for " + filePath + ", please retry shortly."
        );
    }

    private void triggerAsyncUpdate(Path targetFile, String fileUrl, String label) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        String key = normalizePath(targetFile);

        inProgressDownloads.compute(key, (k, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }

            CompletableFuture<Void> task = CompletableFuture.runAsync(
                    () -> tryUpdateOne(targetFile, fileUrl, label),
                    downloadExecutor
            );
            task.whenComplete((unused, throwable) -> inProgressDownloads.remove(k));
            return task;
        });
    }

    private void tryUpdateOne(Path targetFile, String fileUrl, String label) {
        boolean localReady = isValidFile(targetFile);
        try {
            RemoteMeta remoteMeta = fetchRemoteMeta(fileUrl);
            RemoteMeta localMeta = readLocalMeta(targetFile);

            if (localReady && remoteMeta != null && remoteMeta.hasValidators() && localMeta != null
                    && localMeta.sameAs(remoteMeta)) {
                log.debug("Wind {} source unchanged (ETag/Last-Modified), skip download: {}", label, targetFile);
                return;
            }

            RemoteMeta downloadedMeta = downloadTo(fileUrl, targetFile);
            if (!isValidFile(targetFile)) {
                throw new BusinessException(
                        ResultCode.SERVICE_UNAVAILABLE,
                        "Downloaded wind " + label + " file is invalid: " + targetFile
                );
            }
            RemoteMeta metaToSave = downloadedMeta != null && downloadedMeta.hasValidators() ? downloadedMeta : remoteMeta;
            writeLocalMeta(targetFile, metaToSave);
            log.info("Wind {} file updated from URL: {}", label, targetFile);
        } catch (Exception e) {
            if (localReady || isValidFile(targetFile)) {
                log.warn("Update wind {} file failed, fallback to local file: {}, error={}", label, targetFile, e.getMessage());
            } else {
                log.error("Update wind {} file failed and local file unavailable: {}, error={}", label, targetFile, e.getMessage());
            }
        }
    }

    private boolean isValidFile(Path filePath) {
        try {
            return Files.exists(filePath) && Files.isRegularFile(filePath) && Files.size(filePath) >= minFileSizeBytes;
        } catch (IOException e) {
            return false;
        }
    }

    private RemoteMeta fetchRemoteMeta(String fileUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(downloadTimeoutSeconds))
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return RemoteMeta.fromHeaders(response.headers());
            }
            if (status == 405 || status == 501) {
                log.debug("HEAD not supported for {}, fallback to full download", fileUrl);
                return null;
            }
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "HEAD request failed, HTTP status: " + status);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "HEAD request failed: " + e.getMessage());
        }
    }

    private RemoteMeta downloadTo(String fileUrl, Path destination) {
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Cannot create wind data directory: " + e.getMessage());
        }

        Path tempFile = destination.resolveSibling(destination.getFileName() + ".download");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .GET()
                .timeout(Duration.ofSeconds(downloadTimeoutSeconds))
                .build();

        log.info("Downloading wind source file from {}", fileUrl);
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new BusinessException(
                        ResultCode.SERVICE_UNAVAILABLE,
                        "Failed to download wind data, HTTP status: " + status
                );
            }

            long contentLength = parseContentLength(response.headers());
            long startMs = System.currentTimeMillis();
            long intervalMs = Math.max(1, downloadProgressLogIntervalSeconds) * 1000L;
            long nextLogAt = startMs + intervalMs;
            long downloadedBytes = 0L;

            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(
                         tempFile,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE
                 )) {
                int bufferSize = Math.max(8192, downloadBufferSize);
                byte[] buffer = new byte[bufferSize];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloadedBytes += read;

                    long now = System.currentTimeMillis();
                    if (now >= nextLogAt) {
                        logDownloadProgress(destination, downloadedBytes, contentLength, startMs, false);
                        nextLogAt = now + intervalMs;
                    }
                }
                out.flush();
            }

            logDownloadProgress(destination, downloadedBytes, contentLength, startMs, true);

            try {
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tempFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return RemoteMeta.fromHeaders(response.headers());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            try {
                if (Files.exists(tempFile)) {
                    Files.delete(tempFile);
                }
            } catch (IOException ignored) {
            }
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Failed to download wind data: " + e.getMessage());
        }
    }

    private RemoteMeta readLocalMeta(Path dataFile) {
        Path metaFile = metaPath(dataFile);
        if (!Files.exists(metaFile)) {
            return null;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(metaFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            String etag = normalizeHeaderValue(properties.getProperty("etag"));
            String lastModified = normalizeHeaderValue(properties.getProperty("lastModified"));
            if (etag == null && lastModified == null) {
                return null;
            }
            return new RemoteMeta(etag, lastModified);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeLocalMeta(Path dataFile, RemoteMeta meta) {
        Path metaFile = metaPath(dataFile);
        if (meta == null || !meta.hasValidators()) {
            try {
                Files.deleteIfExists(metaFile);
            } catch (IOException ignored) {
            }
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("etag", meta.etag != null ? meta.etag : "");
        properties.setProperty("lastModified", meta.lastModified != null ? meta.lastModified : "");
        try (Writer writer = Files.newBufferedWriter(
                metaFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            properties.store(writer, "wind-source-meta");
        } catch (IOException e) {
            log.warn("Write wind source meta failed for {}: {}", dataFile, e.getMessage());
        }
    }

    private Path metaPath(Path dataFile) {
        return dataFile.resolveSibling(dataFile.getFileName() + ".meta");
    }

    private String normalizeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }

    private String normalizePath(Path file) {
        return file.toAbsolutePath().normalize().toString();
    }

    private long parseContentLength(HttpHeaders headers) {
        Optional<String> value = headers.firstValue("Content-Length");
        if (value.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(value.get().trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private void logDownloadProgress(
            Path destination,
            long downloadedBytes,
            long contentLength,
            long startMs,
            boolean finished) {

        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
        double speedBytesPerSec = downloadedBytes * 1000.0 / elapsedMs;
        String downloadedText = formatBytes(downloadedBytes);
        String speedText = formatBytes((long) speedBytesPerSec) + "/s";

        if (contentLength > 0) {
            double percent = downloadedBytes * 100.0 / contentLength;
            if (finished) {
                log.info(
                        "Wind download completed: file={}, size={}/{}, progress=100.00%, avgSpeed={}",
                        destination,
                        downloadedText,
                        formatBytes(contentLength),
                        speedText
                );
            } else {
                log.info(
                        "Wind download progress: file={}, downloaded={}/{}, progress={}% , speed={}",
                        destination,
                        downloadedText,
                        formatBytes(contentLength),
                        String.format("%.2f", percent),
                        speedText
                );
            }
            return;
        }

        if (finished) {
            log.info(
                    "Wind download completed: file={}, downloaded={}, avgSpeed={}",
                    destination,
                    downloadedText,
                    speedText
            );
        } else {
            log.info(
                    "Wind download progress: file={}, downloaded={}, speed={}",
                    destination,
                    downloadedText,
                    speedText
            );
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private static class RemoteMeta {
        private final String etag;
        private final String lastModified;

        private RemoteMeta(String etag, String lastModified) {
            this.etag = etag;
            this.lastModified = lastModified;
        }

        private static RemoteMeta fromHeaders(HttpHeaders headers) {
            String etag = header(headers, "etag");
            String lastModified = header(headers, "last-modified");
            if (etag == null && lastModified == null) {
                return null;
            }
            return new RemoteMeta(etag, lastModified);
        }

        private static String header(HttpHeaders headers, String name) {
            Optional<String> value = headers.firstValue(name);
            if (value.isEmpty()) {
                return null;
            }
            String v = value.get().trim();
            return v.isEmpty() ? null : v;
        }

        private boolean hasValidators() {
            return etag != null || lastModified != null;
        }

        private boolean sameAs(RemoteMeta other) {
            if (other == null) {
                return false;
            }
            if (etag != null && other.etag != null && etag.equals(other.etag)) {
                return true;
            }
            return lastModified != null && other.lastModified != null && lastModified.equals(other.lastModified);
        }
    }

    @Getter
    public static class WindSourceFiles {
        private final Path uFile;
        private final Path vFile;

        public WindSourceFiles(Path uFile, Path vFile) {
            this.uFile = uFile;
            this.vFile = vFile;
        }
    }
}
