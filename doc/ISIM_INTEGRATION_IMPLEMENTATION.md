# ISIM 集成实现文档

## 一、项目概述

### 1.1 目标
将低空气象保障平台与飞机模拟机ISIM集成，实现：
- **气象数据 → ISIM**：实时发送风向、风速、温度等气象数据给ISIM
- **飞机姿态 → 平台**：ISIM计算后返回飞机姿态数据
- **前端渲染**：在航路分析模块实时渲染飞机起飞动画

### 1.2 技术架构
```
┌─────────────┐     UDP     ┌─────────────┐     WebSocket    ┌─────────────┐
│   ISIM      │────────────>│   后端服务   │───────────────>│    前端      │
│  模拟机     │<────────────│ (Spring Boot)│                 │   (Vue 3)   │
└─────────────┘  气象数据   └─────────────┘   姿态数据      └─────────────┘
```

## 二、后端实现

### 2.1 项目结构
```
src/main/java/com/bluesky/isim/
├── config/
│   ├── IsimConfig.java          # ISIM配置属性
│   └── WebSocketConfig.java     # WebSocket配置
├── model/
│   ├── SimData.java             # ISIM姿态数据模型
│   └── WeatherData.java         # 气象数据模型
├── service/
│   ├── IsimUdpService.java      # UDP通信服务
│   ├── WeatherDataService.java  # 气象数据服务
│   ├── IsimWebSocketService.java # WebSocket服务
│   └── IsimWebSocketServer.java # WebSocket服务器端点
└── controller/
    └── IsimController.java      # REST API控制器（待创建）
```

### 2.2 核心服务说明

#### 2.2.1 IsimUdpService - UDP通信服务
**功能**：
- 监听UDP端口（默认8151）接收ISIM姿态数据
- 发送UDP数据到ISIM（默认端口8152）
- 解析ISIM数据格式（分号分隔字符串）
- 定时发送气象数据

**关键配置**：
```yaml
# application.yml
isim:
  host: 127.0.0.1           # ISIM IP地址
  send-port: 8152           # 发送气象数据端口
  receive-port: 8151        # 接收姿态数据端口
  send-interval: 1000       # 发送间隔（毫秒）
  enabled: true             # 是否启用集成
```

**数据格式**：
- **接收格式**：分号分隔的17个字段
  ```
  aircraftRoll;aircraftPitch;aircraftHeading;aircraftLon;aircraftLat;aircraftAlt;
  eyeLon;eyeLat;eyeAlt;trailHide;airwayHide;observeLon;observeLat;observeAlt;
  observePitch;observeHeading;ownshipLight
  ```

- **发送格式**：分号分隔的11个气象字段
  ```
  windDirection;windSpeed;temperature;humidity;pressure;visibility;
  cloudCover;precipitation;longitude;latitude;altitude
  ```

#### 2.2.2 WeatherDataService - 气象数据服务
**功能**：
- 从数据库获取实时气象数据
- 转换为ISIM格式
- 提供默认数据（后备方案）
- 支持湍流和风切变数据转换

#### 2.2.3 IsimWebSocketService - WebSocket服务
**功能**：
- 管理WebSocket连接
- 广播姿态数据给所有前端连接
- 提供连接状态管理

**WebSocket端点**：
```
ws://localhost:8080/ws/isim-data
```

### 2.3 依赖配置

#### 2.3.1 pom.xml 添加依赖
```xml
<!-- WebSocket -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

#### 2.3.2 application.yml 配置
```yaml
# ISIM配置
isim:
  host: 127.0.0.1
  send-port: 8152
  receive-port: 8151
  send-interval: 1000
  enabled: true
  websocket-path: /ws/isim-data

# WebSocket配置
server:
  port: 8080

# 日志配置
logging:
  level:
    com.bluesky.isim: DEBUG
```

### 2.4 启动流程
1. **服务启动**：`@PostConstruct` 初始化UDP套接字
2. **接收线程**：启动UDP监听线程
3. **定时发送**：`@Scheduled` 定时发送气象数据
4. **WebSocket**：等待前端连接

### 2.5 API接口

#### 2.5.1 WebSocket接口
- **连接地址**：`ws://localhost:8080/ws/isim-data`
- **数据格式**：JSON
- **示例数据**：
```json
{
  "header": "UE5_SIM_DATA",
  "aircraftRoll": 0.5,
  "aircraftPitch": 2.3,
  "aircraftHeading": 45.0,
  "aircraftLon": 120.3844,
  "aircraftLat": 36.1052,
  "aircraftAlt": 100.0,
  "eyeLon": 120.3845,
  "eyeLat": 36.1053,
  "eyeAlt": 101.0,
  "trailHide": 0,
  "airwayHide": 0,
  "observeLon": 120.3850,
  "observeLat": 36.1060,
  "observeAlt": 150.0,
  "observePitch": 10.0,
  "observeHeading": 90.0,
  "ownshipLight": 1,
  "timestamp": "2026-03-02T11:30:00",
  "source": "ISIM"
}
```

#### 2.5.2 REST API接口（待实现）
```java
@RestController
@RequestMapping("/api/isim")
public class IsimController {
    // 1. 获取ISIM连接状态
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus()
    
    // 2. 手动发送气象数据
    @PostMapping("/send-weather")
    public Result<Void> sendWeatherData(@RequestBody WeatherRequest request)
    
    // 3. 控制数据传输
    @PostMapping("/control")
    public Result<Void> controlDataFlow(@RequestBody ControlRequest request)
}
```

## 三、前端实现

### 3.1 航路分析模块集成

#### 3.1.1 项目结构
```
src/
├── components/business/RouteAnalysis/
│   ├── IsimAnimation.vue      # 飞机动画组件
│   └── RouteAnalysisPanel.vue # 航路分析主面板
├── hooks/
│   └── useIsimWebSocket.js    # ISIM WebSocket Hook
├── utils/
│   └── isimDataParser.js      # ISIM数据解析工具
└── stores/
    └── isimStore.js           # ISIM状态管理
```

#### 3.1.2 飞机动画组件 (IsimAnimation.vue)
```vue
<template>
  <div class="isim-animation-container">
    <!-- 3D动画容器 -->
    <div ref="animationContainer" class="animation-container"></div>
    
    <!-- 状态面板 -->
    <div class="status-panel">
      <div class="status-item">
        <span>姿态：</span>
        <span>滚转 {{ aircraftRoll.toFixed(2) }}°</span>
        <span>俯仰 {{ aircraftPitch.toFixed(2) }}°</span>
        <span>航向 {{ aircraftHeading.toFixed(2) }}°</span>
      </div>
      <div class="status-item">
        <span>位置：</span>
        <span>经度 {{ aircraftLon.toFixed(6) }}</span>
        <span>纬度 {{ aircraftLat.toFixed(6) }}</span>
        <span>高度 {{ aircraftAlt.toFixed(2) }}m</span>
      </div>
      <div class="status-item">
        <span>连接状态：</span>
        <span :class="connectionStatusClass">{{ connectionStatusText }}</span>
      </div>
    </div>
    
    <!-- 控制面板 -->
    <div class="control-panel">
      <button @click="toggleConnection" :disabled="isConnecting">
        {{ isConnected ? '断开连接' : '连接ISIM' }}
      </button>
      <button @click="startAnimation" :disabled="!isConnected || isAnimating">
        {{ isAnimating ? '停止动画' : '开始起飞' }}
      </button>
      <button @click="resetAnimation" :disabled="!isConnected">
        重置
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useIsimWebSocket } from '@/hooks/useIsimWebSocket'
import { useIsimStore } from '@/stores/isimStore'

// 状态管理
const isimStore = useIsimStore()
const { connect, disconnect, isConnected, isConnecting } = useIsimWebSocket()

// 动画状态
const isAnimating = ref(false)
const animationContainer = ref(null)

// 飞机姿态数据
const aircraftRoll = computed(() => isimStore.simData?.aircraftRoll || 0)
const aircraftPitch = computed(() => isimStore.simData?.aircraftPitch || 0)
const aircraftHeading = computed(() => isimStore.simData?.aircraftHeading || 0)
const aircraftLon = computed(() => isimStore.simData?.aircraftLon || 0)
const aircraftLat = computed(() => isimStore.simData?.aircraftLat || 0)
const aircraftAlt = computed(() => isimStore.simData?.aircraftAlt || 0)

// 连接状态
const connectionStatusText = computed(() => {
  if (isConnecting.value) return '连接中...'
  return isConnected.value ? '已连接' : '未连接'
})

const connectionStatusClass = computed(() => ({
  'status-connected': isConnected.value,
  'status-connecting': isConnecting.value,
  'status-disconnected': !isConnected.value && !isConnecting.value
}))

// 方法
const toggleConnection = async () => {
  if (isConnected.value) {
    await disconnect()
    stopAnimation()
  } else {
    await connect()
  }
}

const startAnimation = () => {
  if (!isAnimating.value) {
    isAnimating.value = true
    // 发送起飞指令给后端
    sendTakeoffCommand()
    // 开始渲染动画
    startRendering()
  } else {
    stopAnimation()
  }
}

const stopAnimation = () => {
  isAnimating.value = false
  // 停止渲染
  stopRendering()
}

const resetAnimation = () => {
  stopAnimation()
  // 重置飞机位置
  isimStore.resetPosition()
}

// 3D渲染相关
let renderer = null
let scene = null
let camera = null
let aircraft = null
let animationId = null

const initThreeJS = () => {
  // 初始化Three.js场景
  // 具体实现根据实际3D库决定
}

const startRendering = () => {
  const animate = () => {
    if (!isAnimating.value) return
    
    // 更新飞机姿态
    updateAircraftPosition()
    
    // 渲染场景
    renderer.render(scene, camera)
    
    animationId = requestAnimationFrame(animate)
  }
  
  animate()
}

const updateAircraftPosition = () => {
  if (!aircraft) return
  
  // 根据simData更新飞机位置和姿态
  aircraft.rotation.x = aircraftRoll.value * Math.PI / 180
  aircraft.rotation.y = aircraftPitch.value * Math.PI / 180
  aircraft.rotation.z = aircraftHeading.value * Math.PI / 180
  
  aircraft.position.x = (aircraftLon.value - 120) * 100000 // 简化映射
  aircraft.position.y = aircraftAlt.value / 10
  aircraft.position.z = (aircraftLat.value - 36) * 100000
}

const stopRendering = () => {
  if (animationId) {
    cancelAnimationFrame(animationId)
    animationId = null
  }
}

// 发送起飞指令
const sendTakeoffCommand = async () => {
  try {
    const response = await fetch('/api/isim/control', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        command: 'TAKEOFF',
        timestamp: new Date().toISOString()
      })
    })
    
    if (!response.ok) {
      console.error('起飞指令发送失败')
    }
  } catch (error) {
    console.error('发送起飞指令出错:', error)
  }
}

// 生命周期
onMounted(() => {
  initThreeJS()
})

onUnmounted(() => {
  stopAnimation()
  if (isConnected.value) {
    disconnect()
  }
})
</script>

<style scoped>
.isim-animation-container {
  position: relative;
  width: 100%;
  height: 600px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  overflow: hidden;
}

.animation-container {
  width: 100%;
  height: 70%;
}

.status-panel {
  position: absolute;
  bottom: 80px;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.status-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.status-item span:first-child {
  font-weight: bold;
  color: #4ade80;
}

.status-connected {
  color: #4ade80;
}

.status-connecting {
  color: #fbbf24;
}

.status-disconnected {
  color: #f87171;
}

.control-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.8);
  padding: 16px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.control-panel button {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: #3b82f6;
  color: white;
  cursor: pointer;
  font-weight: bold;
  transition: background 0.3s;
}

.control-panel button:hover:not(:disabled) {
  background: #2563eb;
}

.control-panel button:disabled {
  background: #6b7280;
  cursor: not-allowed;
}
</style>
```

#### 3.1.3 WebSocket Hook (useIsimWebSocket.js)
```javascript
import { ref, onUnmounted } from 'vue'
import { useIsimStore } from '@/stores/isimStore'

export function useIsimWebSocket() {
  const isimStore = useIsimStore()
  
  const ws = ref(null)
  const isConnected = ref(false)
  const isConnecting = ref(false)
  const reconnectAttempts = ref(0)
  const maxReconnectAttempts = 5
  
  // WebSocket URL (从环境变量获取)
  const wsUrl = import.meta.env.VITE_ISIM_WS_URL || 'ws://localhost:8080/ws/isim-data'
  
  // 连接WebSocket
  const connect = () => {
    if (isConnecting.value || isConnected.value) {
      return Promise.resolve()
    }
    
    return new Promise((resolve, reject) => {
      isConnecting.value = true
      
      try {
        ws.value = new WebSocket(wsUrl)
        
        ws.value.onopen = () => {
          console.log('ISIM WebSocket连接成功')
          isConnected.value = true
          isConnecting.value = false
          reconnectAttempts.value = 0
          resolve()
        }
        
        ws.value.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)
            isimStore.updateSimData(data)
          } catch (error) {
            console.error('解析ISIM数据失败:', error)
          }
        }
        
        ws.value.onerror = (error) => {
          console.error('ISIM WebSocket错误:', error)
          isConnecting.value = false
          reject(error)
        }
        
        ws.value.onclose = () => {
          console.log('ISIM WebSocket连接关闭')
          isConnected.value = false
          isConnecting.value = false
          
          // 自动重连
          if (reconnectAttempts.value < maxReconnectAttempts) {
            reconnectAttempts.value++
            setTimeout(() => {
              console.log(`尝试重连 (${reconnectAttempts.value}/${maxReconnectAttempts})`)
              connect()
            }, 3000)
          }
        }
      } catch (error) {
        isConnecting.value = false
        reject(error)
      }
    })
  }
  
  // 断开连接
  const disconnect = () => {
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    isConnected.value = false
    isConnecting.value = false
    reconnectAttempts.value = 0
  }
  
  // 发送消息到后端
  const sendMessage = (message) => {
    if (ws.value && isConnected.value) {
      ws.value.send(JSON.stringify(message))
    }
  }
  
  // 组件卸载时清理
  onUnmounted(() => {
    disconnect()
  })
  
  return {
    ws,
    isConnected,
    isConnecting,
    connect,
    disconnect,
    sendMessage
  }
}
```

#### 3.1.4 状态管理 (isimStore.js)
```javascript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useIsimStore = defineStore('isim', () => {
  // 状态
  const simData = ref(null)
  const connectionStatus = ref('disconnected') // disconnected, connecting, connected
  const animationStatus = ref('stopped') // stopped, starting, flying, landing
  const flightPath = ref([]) // 飞行轨迹
  
  // 计算属性
  const isConnected = computed(() => connectionStatus.value === 'connected')
  const isAnimating = computed(() => animationStatus.value !== 'stopped')
  
  // 动作
  const updateSimData = (data) => {
    simData.value = data
    
    // 记录飞行轨迹
    if (data.aircraftLon && data.aircraftLat && data.aircraftAlt) {
      flightPath.value.push({
        lon: data.aircraftLon,
        lat: data.aircraftLat,
        alt: data.aircraftAlt,
        timestamp: new Date().toISOString()
      })
      
      // 保持最多1000个点
      if (flightPath.value.length > 1000) {
        flightPath.value.shift()
      }
    }
  }
  
  const updateConnectionStatus = (status) => {
    connectionStatus.value = status
  }
  
  const updateAnimationStatus = (status) => {
    animationStatus.value = status
  }
  
  const resetPosition = () => {
    flightPath.value = []
    // 重置为初始位置
    simData.value = {
      aircraftRoll: 0,
      aircraftPitch: 0,
      aircraftHeading: 0,
      aircraftLon: 120.3844,
      aircraftLat: 36.1052,
      aircraftAlt: 100,
      eyeLon: 120.3845,
      eyeLat: 36.1053,
      eyeAlt: 101,
      trailHide: 0,
      airwayHide: 0,
      observeLon: 120.3850,
      observeLat: 36.1060,
      observeAlt: 150,
      observePitch: 10,
      observeHeading: 90,
      ownshipLight: 1,
      timestamp: new Date().toISOString(),
      source: 'RESET'
    }
  }
  
  return {
    // 状态
    simData,
    connectionStatus,
    animationStatus,
    flightPath,
    
    // 计算属性
    isConnected,
    isAnimating,
    
    // 动作
    updateSimData,
    updateConnectionStatus,
    updateAnimationStatus,
    resetPosition
  }
})
```

#### 3.1.5 数据解析工具 (isimDataParser.js)
```javascript
/**
 * ISIM数据解析工具
 */

/**
 * 解析原始ISIM数据字符串
 * @param {string} rawData - 原始数据字符串
 * @returns {object} 解析后的数据对象
 */
export function parseIsimData(rawData) {
  if (!rawData || typeof rawData !== 'string') {
    return null
  }
  
  // 清理数据
  const cleanedData = rawData
    .replaceAll('[^0-9.;truefalse-]', '')
    .replaceAll(';+$', '')
  
  const fields = cleanedData.split(';')
  
  if (fields.length < 17) {
    console.warn(`ISIM数据字段不足: ${fields.length}/17`)
    return null
  }
  
  // 解析字段
  return {
    header: 'UE5_SIM_DATA',
    aircraftRoll: parseFloat(fields[0]) || 0,
    aircraftPitch: parseFloat(fields[1]) || 0,
    aircraftHeading: parseFloat(fields[2]) || 0,
    aircraftLon: parseFloat(fields[3]) || 0,
    aircraftLat: parseFloat(fields[4]) || 0,
    aircraftAlt: parseFloat(fields[5]) || 0,
    eyeLon: parseFloat(fields[6]) || 0,
    eyeLat: parseFloat(fields[7]) || 0,
    eyeAlt: parseFloat(fields[8]) || 0,
    trailHide: parseBoolean(fields[9]) ? 1 : 0,
    airwayHide: parseBoolean(fields[10]) ? 1 : 0,
    observeLon: parseFloat(fields[11]) || 0,
    observeLat: parseFloat(fields[12]) || 0,
    observeAlt: parseFloat(fields[13]) || 0,
    observePitch: parseFloat(fields[14]) || 0,
    observeHeading: parseFloat(fields[15]) || 0,
    ownshipLight: parseBoolean(fields[16]) ? 1 : 0,
    timestamp: new Date().toISOString(),
    source: 'ISIM'
  }
}

/**
 * 解析布尔值
 */
function parseBoolean(str) {
  if (typeof str === 'boolean') return str
  if (typeof str === 'number') return str !== 0
  if (typeof str === 'string') {
    if (str.toLowerCase() === 'true') return true
    if (str.toLowerCase() === 'false') return false
    const num = parseFloat(str)
    return !isNaN(num) && num !== 0
  }
  return false
}

/**
 * 格式化飞机姿态数据
 */
export function formatAircraftAttitude(data) {
  if (!data) return {}
  
  return {
    roll: data.aircraftRoll?.toFixed(2) || '0.00',
    pitch: data.aircraftPitch?.toFixed(2) || '0.00',
    heading: data.aircraftHeading?.toFixed(2) || '0.00',
    altitude: data.aircraftAlt?.toFixed(2) || '0.00',
    speed: calculateSpeed(data) || '0.00'
  }
}

/**
 * 计算飞机速度（简化版）
 */
function calculateSpeed(data) {
  // 这里需要根据实际情况计算速度
  // 可以使用位置变化和时间差来计算
  return '0.00'
}

/**
 * 检查数据有效性
 */
export function validateIsimData(data) {
  if (!data) return false
  
  // 检查必要字段
  const requiredFields = ['aircraftLon', 'aircraftLat', 'aircraftAlt']
  for (const field of requiredFields) {
    if (data[field] === undefined || data[field] === null) {
      return false
    }
  }
  
  // 检查数值范围
  if (Math.abs(data.aircraftRoll) > 180) return false
  if (Math.abs(data.aircraftPitch) > 90) return false
  if (data.aircraftHeading < 0 || data.aircraftHeading >= 360) return false
  
  return true
}
```

### 3.2 集成到航路分析模块

#### 3.2.1 修改 RouteAnalysisPanel.vue
```vue
<template>
  <div class="route-analysis-panel">
    <!-- 原有内容... -->
    
    <!-- ISIM动画区域 -->
    <div class="isim-section" v-if="showIsimAnimation">
      <h3>飞机起飞动画模拟</h3>
      <p class="description">实时连接ISIM模拟机，展示飞机在气象条件下的起飞姿态</p>
      
      <IsimAnimation ref="isimAnimation" />
      
      <div class="integration-controls">
        <div class="control-group">
          <label>
            <input type="checkbox" v-model="autoSendWeather" />
            自动发送气象数据
          </label>
          <span class="hint">每秒向ISIM发送当前监测点气象数据</span>
        </div>
        
        <div class="control-group">
          <label>
            <input type="checkbox" v-model="recordFlightPath" />
            记录飞行轨迹
          </label>
          <span class="hint">保存飞机飞行路径用于分析</span>
        </div>
        
        <button @click="exportFlightData" class="export-btn">
          导出飞行数据
        </button>
      </div>
    </div>
    
    <!-- 集成控制按钮 -->
    <div class="integration-toggle">
      <button @click="toggleIsimIntegration" class="toggle-btn">
        {{ showIsimAnimation ? '隐藏' : '显示' }} ISIM动画
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import IsimAnimation from './IsimAnimation.vue'
import { useIsimStore } from '@/stores/isimStore'

const isimStore = useIsimStore()
const isimAnimation = ref(null)

// 控制状态
const showIsimAnimation = ref(false)
const autoSendWeather = ref(true)
const recordFlightPath = ref(true)

// 切换ISIM集成显示
const toggleIsimIntegration = () => {
  showIsimAnimation.value = !showIsimAnimation.value
  
  if (showIsimAnimation.value) {
    // 显示时自动连接
    setTimeout(() => {
      if (isimAnimation.value?.connect) {
        isimAnimation.value.connect()
      }
    }, 100)
  } else {
    // 隐藏时断开连接
    if (isimAnimation.value?.disconnect) {
      isimAnimation.value.disconnect()
    }
  }
}

// 导出飞行数据
const exportFlightData = () => {
  const flightPath = isimStore.flightPath
  if (!flightPath.length) {
    alert('没有可导出的飞行数据')
    return
  }
  
  const dataStr = JSON.stringify(flightPath, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })
  
  const url = URL.createObjectURL(dataBlob)
  const link = document.createElement('a')
  link.href = url
  link.download = `flight-path-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

// 监听航路分析状态
watch(() => routeAnalysisStore.currentRoute, (newRoute) => {
  if (newRoute && showIsimAnimation.value) {
    // 当选择航路时，自动设置飞机起始位置
    isimStore.resetPosition()
    if (newRoute.startPoint) {
      // 更新飞机位置到航路起点
      // 具体实现根据航路数据结构调整
    }
  }
})
</script>

<style scoped>
.isim-section {
  margin-top: 24px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.description {
  color: #64748b;
  margin-bottom: 16px;
  font-size: 14px;
}

.integration-controls {
  margin-top: 20px;
  padding: 16px;
  background: white;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.control-group {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-group label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.hint {
  color: #94a3b8;
  font-size: 12px;
  margin-left: 8px;
}

.export-btn {
  padding: 8px 16px;
  background: #10b981;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
}

.export-btn:hover {
  background: #059669;
}

.integration-toggle {
  margin-top: 16px;
  text-align: center;
}

.toggle-btn {
  padding: 10px 24px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: bold;
  transition: background 0.3s;
}

.toggle-btn:hover {
  background: #2563eb;
}
</style>
```

### 3.3 环境配置

#### 3.3.1 .env.development
```env
# ISIM WebSocket配置
VITE_ISIM_WS_URL=ws://localhost:8080/ws/isim-data
VITE_ISIM_API_BASE=/api/isim

# 3D渲染配置
VITE_ENABLE_3D_RENDERING=true
VITE_MAP_CENTER_LON=120.3844
VITE_MAP_CENTER_LAT=36.1052
VITE_MAP_ZOOM=12
```

#### 3.3.2 vite.config.js 添加环境变量支持
```javascript
export default defineConfig({
  // ... 其他配置
  
  define: {
    'process.env': process.env
  }
})
```

## 四、ISIM端修改

### 4.1 修改要求

#### 4.1.1 接收气象数据
- **监听端口**：8152 (UDP)
- **数据格式**：分号分隔的字符串
  ```
  windDirection;windSpeed;temperature;humidity;pressure;visibility;
  cloudCover;precipitation;longitude;latitude;altitude
  ```
- **处理逻辑**：将气象数据应用到飞行算法中

#### 4.1.2 发送姿态数据
- **发送端口**：8151 (UDP)
- **数据格式**：分号分隔的17个字段（见2.2.1）
- **发送频率**：建议10-30Hz

### 4.2 C++修改示例

基于 `UE5VisualUnit.cpp` 的修改：

```cpp
// 添加气象数据接收
void ReceiveWeatherData() {
    // 创建UDP接收套接字
    SOCKET weatherSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    
    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(8152);  // 监听端口
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    
    bind(weatherSocket, (sockaddr*)&serverAddr, sizeof(serverAddr));
    
    char buffer[1024];
    sockaddr_in clientAddr;
    int clientAddrSize = sizeof(clientAddr);
    
    while (true) {
        int bytesReceived = recvfrom(weatherSocket, buffer, sizeof(buffer), 0,
                                    (sockaddr*)&clientAddr, &clientAddrSize);
        
        if (bytesReceived > 0) {
            buffer[bytesReceived] = '\0';
            std::string weatherStr(buffer);
            
            // 解析气象数据
            ParseWeatherData(weatherStr);
            
            // 应用到飞行算法
            ApplyWeatherToFlightModel(parsedWeather);
        }
    }
}

// 修改现有发送逻辑，确保发送正确的数据格式
void SendSimulationData() {
    // 原有发送逻辑...
    
    // 构建数据字符串（分号分隔）
    std::stringstream dataStream;
    dataStream << aircraftRoll << ";"
               << aircraftPitch << ";"
               << aircraftHeading << ";"
               << aircraftLon << ";"
               << aircraftLat << ";"
               << aircraftAlt << ";"
               << eyeLon << ";"
               << eyeLat << ";"
               << eyeAlt << ";"
               << trailHide << ";"
               << airwayHide << ";"
               << observeLon << ";"
               << observeLat << ";"
               << observeAlt << ";"
               << observePitch << ";"
               << observeHeading << ";"
               << ownshipLight;
    
    std::string dataStr = dataStream.str();
    
    // 发送到后端（端口8151）
    SendUdpData(dataStr, "127.0.0.1", 8151);
}
```

## 五、部署与测试

### 5.1 部署步骤

#### 5.1.1 后端部署
1. **编译打包**
   ```bash
   cd /mnt/e/MyProduct/bluesky-server
   mvn clean package -DskipTests
   ```

2. **配置文件**
   ```bash
   # 创建应用配置文件
   cp src/main/resources/application.yml target/application-custom.yml
   
   # 修改配置（根据实际环境）
   vim target/application-custom.yml
   ```

3. **启动服务**
   ```bash
   java -jar target/bluesky-server.jar \
     --spring.config.location=application-custom.yml
   ```

#### 5.1.2 前端部署
1. **安装依赖**
   ```bash
   cd /mnt/e/MyProduct/low-altitude-meteorological-service
   npm install
   ```

2. **环境配置**
   ```bash
   cp .env.development .env.production
   # 修改生产环境配置
   ```

3. **构建**
   ```bash
   npm run build
   ```

4. **部署到Nginx**
   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           root /path/to/dist;
           index index.html;
           try_files $uri $uri/ /index.html;
       }
       
       location /api {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
       
       location /ws {
           proxy_pass http://localhost:8080;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }
   }
   ```

### 5.2 测试流程

#### 5.2.1 单元测试
1. **后端测试**
   ```bash
   mvn test
   ```

2. **前端测试**
   ```bash
   npm run test:unit
   ```

#### 5.2.2 集成测试
1. **启动测试环境**
   ```
   后端服务 → ISIM模拟机 → 前端应用
   ```

2. **测试场景**
   - 连接测试：验证WebSocket连接
   - 数据传输：验证UDP数据发送/接收
   - 动画渲染：验证前端3D渲染
   - 性能测试：验证实时性要求

#### 5.2.3 端到端测试
```javascript
// 测试脚本示例
describe('ISIM集成测试', () => {
  it('应该成功连接WebSocket', async () => {
    const ws = new WebSocket('ws://localhost:8080/ws/isim-data')
    await expectConnection(ws)
  })
  
  it('应该接收ISIM数据', async () => {
    // 模拟ISIM发送数据
    sendTestUdpData()
    
    // 验证前端接收
    await waitForData()
  })
  
  it('应该渲染飞机动画', () => {
    // 验证3D渲染
    expect(animationContainer).toBeVisible()
    expect(aircraftModel).toBeInScene()
  })
})
```

## 六、故障排除

### 6.1 常见问题

#### 6.1.1 连接问题
- **WebSocket无法连接**：检查后端服务是否运行，端口是否正确
- **UDP数据无法接收**：检查防火墙设置，端口是否被占用
- **ISIM无法连接**：确认IP地址和端口配置正确

#### 6.1.2 数据问题
- **数据格式错误**：检查数据解析逻辑，确保字段顺序正确
- **数据延迟**：优化网络配置，减少数据处理时间
- **数据丢失**：增加数据验证和重传机制

#### 6.1.3 渲染问题
- **3D渲染卡顿**：优化渲染逻辑，减少不必要的重绘
- **内存泄漏**：确保正确清理Three.js资源
- **浏览器兼容性**：测试不同浏览器支持

### 6.2 监控与日志

#### 6.2.1 后端监控
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

#### 6.2.2 前端监控
```javascript
// 添加性能监控
const perfObserver = new PerformanceObserver((list) => {
  list.getEntries().forEach(entry => {
    console.log(`[Performance] ${entry.name}: ${entry.duration}ms`)
  })
})

perfObserver.observe({ entryTypes: ['measure', 'resource'] })
```

## 七、性能优化

### 7.1 后端优化
- **UDP缓冲区**：调整缓冲区大小，避免数据丢失
- **线程池**：合理配置线程池大小
- **序列化**：使用高效的JSON序列化库

### 7.2 前端优化
- **WebSocket消息**：减少不必要的数据传输
- **3D渲染**：使用LOD（层次细节）优化
- **内存管理**：及时清理不再使用的资源

### 7.3 网络优化
- **数据压缩**：考虑使用二进制格式替代JSON
- **连接复用**：保持长连接，减少连接建立开销
- **本地缓存**：缓存静态资源，减少网络请求

---

## 附录

### A. 数据字段说明

#### ISIM姿态数据字段
| 字段名 | 类型 | 说明 | 单位 |
|--------|------|------|------|
| aircraftRoll | double | 飞机滚转角 | 度 |
| aircraftPitch | double | 飞机俯仰角 | 度 |
| aircraftHeading | double | 飞机真航向 | 度 |
| aircraftLon | double | 飞机重心经度 | 度 |
| aircraftLat | double | 飞机重心纬度 | 度 |
| aircraftAlt | double | 飞机重心高度 | 米 |
| eyeLon | double | 眼点经度 | 度 |
| eyeLat | double | 眼点纬度 | 度 |
| eyeAlt | double | 眼点高度 | 米 |
| trailHide | int | 隐藏尾迹 | 0/1 |
| airwayHide | int | 隐藏航路 | 0/1 |
| observeLon | double | 第三视角经度 | 度 |
| observeLat | double | 第三视角纬度 | 度 |
| observeAlt | double | 第三视角高度 | 米 |
| observePitch | double | 第三视角俯仰角 | 度 |
| observeHeading | double | 第三视角航向 | 度 |
| ownshipLight | int | 本机灯光 | 0/1 |

#### 气象数据字段
| 字段名 | 类型 | 说明 | 单位 |
|--------|------|------|------|
| windDirection | decimal | 风向 | 度 |
| windSpeed | decimal | 风速 | 米/秒 |
| temperature | decimal | 温度 | 摄氏度 |
| humidity | decimal | 湿度 | % |
| pressure | decimal | 气压 | 百帕 |
| visibility | decimal | 能见度 | 米 |
| cloudCover | decimal | 云量 | % |
| precipitation | decimal | 降水量 | 毫米/小时 |
| longitude | decimal | 经度 | 度 |
| latitude | decimal | 纬度 | 度 |
| altitude | decimal | 海拔 | 米 |

### B. API接口文档

#### WebSocket接口
- **地址**: `ws://{host}:{port}/ws/isim-data`
- **协议**: WebSocket
- **数据格式**: JSON

#### REST API接口
```http
GET /api/isim/status
POST /api/isim/send-weather
POST /api/isim/control
GET /api/isim/flight-path
```

### C. 配置参考

#### 完整application.yml
```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: bluesky-server
  
  datasource:
    url: jdbc:postgresql://localhost:5432/bluesky
    username: postgres
    password: password
    driver-class-name: org.postgresql.Driver
  
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

isim:
  host: 127.0.0.1
  send-port: 8152
  receive-port: 8151
  send-interval: 1000
  enabled: true
  websocket-path: /ws/isim-data

logging:
  level:
    com.bluesky.isim: DEBUG
    org.springframework.web.socket: INFO
```

---

**文档版本**: 1.0  
**最后更新**: 2026-03-02  
**维护者**: 低空气象保障平台开发团队