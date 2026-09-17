package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.DuplexProtocol
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VioletAccent

private const val PYTHON_SAMPLE_SERVER = """# Python FastAPI 全双工语音服务端对接示例 (端口 8080)
# 对应路由: /ws/duplex 或 /v1/realtime
# 依赖: pip install fastapi uvicorn websockets

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import json
import time

app = FastAPI()

@app.websocket("/ws/duplex")
@app.websocket("/v1/realtime")
async def duplex_endpoint(websocket: WebSocket):
    await websocket.accept()
    
    # 1. 服务端主动推送 session.ready 首包 (16kHz上行, 24kHz下行)
    await websocket.send_text(json.dumps({
        "type": "session.ready",
        "session_id": f"sess_{int(time.time())}",
        "model": "Qwen2.5-Omni-7B",
        "uplink": {"sample_rate": 16000, "channels": 1, "format": "pcm16"},
        "downlink": {"sample_rate": 24000, "channels": 1, "format": "pcm16"}
    }))
    
    try:
        while True:
            msg = await websocket.receive()
            if "bytes" in msg:
                # 方案A: 收到 16kHz 16bit PCM 裸流音频帧 (40ms=1280字节)
                pcm_data = msg["bytes"]
                # 送入实时 ASR 引擎...
            elif "text" in msg:
                data = json.loads(msg["text"])
                mtype = data.get("type")
                if mtype == "input_audio_buffer.append":
                    # 方案B: 收到 Base64 编码的音频帧
                    pass
                elif mtype == "response.cancel":
                    # 收到打断 (Barge-in) 信令！立即取消正在生成的 LLM 与 TTS 流
                    print("⚠️ 客户端打断！立即取消当前回答！")
                    await websocket.send_text(json.dumps({
                        "type": "response.interrupted",
                        "response_id": 1
                    }))
                elif mtype == "ping":
                    # 响应心跳, 原样回显 ts
                    await websocket.send_text(json.dumps({
                        "type": "pong",
                        "ts": data.get("ts", int(time.time() * 1000))
                    }))
                elif mtype == "session.finish":
                    break
    except WebSocketDisconnect:
        print("客户端断开")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolSpecSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("protocol_spec_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.IntegrationInstructions,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "服务端对接规范与协议文档",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Copy Python Server Button
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("FastAPI Server Code", PYTHON_SAMPLE_SERVER)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "已复制 Python 服务端代码到剪贴板！", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VioletAccent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("一键复制 Python 后端对接模板代码 (8080端口)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Code Preview
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "FastAPI 快速对接模板 (支持模拟器 10.0.2.2:8080):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Text(
                            text = PYTHON_SAMPLE_SERVER,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Protocol Docs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = DuplexProtocol.SPECIFICATION_DOCS_MARKDOWN,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        ),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
