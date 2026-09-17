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
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAccent

private const val PYTHON_SAMPLE_SERVER = """# Python FastAPI 全双工语音测试服务端示例
# 依赖: pip install fastapi uvicorn websockets
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import json
import time

app = FastAPI()

@app.websocket("/ws/audio")
async def audio_websocket(websocket: WebSocket):
    await websocket.accept()
    print("全双工客户端已连接")
    
    # 1. 响应握手
    await websocket.send_text(json.dumps({
        "type": "session_ready",
        "session_id": "sess_001"
    }))
    
    try:
        while True:
            message = await websocket.receive()
            if "bytes" in message:
                # 收到客户端实时 16kHz PCM 音频数据帧 (Opcode 0x2)
                pcm_data = message["bytes"]
                # 可以在这里送入 ASR 流式识别引擎 (如 FunASR / Whisper / Paraformer)
                pass
            elif "text" in message:
                data = json.loads(message["text"])
                msg_type = data.get("type")
                
                if msg_type == "session_start":
                    print(f"会话启动参数: {data}")
                elif msg_type == "user_interrupt":
                    print("⚠️ 收到客户端打断(Barge-in)信令！立即取消LLM和TTS生成！")
                elif msg_type == "ping":
                    # 响应心跳以计算 RTT 延迟
                    await websocket.send_text(json.dumps({
                        "type": "pong",
                        "client_timestamp": data.get("client_timestamp", 0),
                        "server_timestamp": int(time.time() * 1000)
                    }))
    except WebSocketDisconnect:
        print("客户端断开连接")

if __name__ == "__main__":
    import uvicorn
    # 0.0.0.0 允许 Android 模拟器(10.0.2.2) 或同一局域网手机访问
    uvicorn.run(app, host="0.0.0.0", port=8000)
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
                Text("一键复制 Python 后端对接模板代码")
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
                        text = "FastAPI 快速对接模板 (支持模拟器 10.0.2.2:8000):",
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
