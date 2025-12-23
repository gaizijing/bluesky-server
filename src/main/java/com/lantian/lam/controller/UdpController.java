package com.lantian.lam.controller;

import com.lantian.lam.service.UdpReceiverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UdpController {
    
    @Autowired
    private UdpReceiverService udpReceiverService;
    
    @PostMapping("/start-udp")
    public ResponseEntity<String> startUdp() {
        udpReceiverService.startReceiving();
        return ResponseEntity.ok("UDP 接收器已启动");
    }
}
