package com.aether.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JSONObject
)

data class McpResourceDefinition(
    val uri: String,
    val name: String,
    val mimeType: String
)

data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Long,
    val method: String,
    val params: JSONObject? = null
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("jsonrpc", jsonrpc)
        obj.put("id", id)
        obj.put("method", method)
        if (params != null) {
            obj.put("params", params)
        }
        return obj.toString()
    }
}

data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Long,
    val result: JSONObject? = null,
    val error: JSONObject? = null
)

class McpClientRuntime(val serverName: String = "AetherInternalMcpServer") {

    private val idCounter = AtomicLong(1L)
    private val pendingRequests = ConcurrentHashMap<Long, (JsonRpcResponse) -> Unit>()
    private val registeredTools = mutableListOf<McpToolDefinition>()
    private val registeredResources = mutableListOf<McpResourceDefinition>()

    var isInitialized: Boolean = false
        private set

    init {
        registerDefaultMcpTools()
        registerDefaultMcpResources()
    }

    private fun registerDefaultMcpTools() {
        registeredTools.add(
            McpToolDefinition(
                name = "filesystem/read",
                description = "Read file contents from sandboxed workspace",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().put("type", "string"))
                    })
                    put("required", JSONArray().put("path"))
                }
            )
        )
        registeredTools.add(
            McpToolDefinition(
                name = "diagnostics/audit",
                description = "Execute static code analysis and security scanner",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("deepScan", JSONObject().put("type", "boolean"))
                    })
                }
            )
        )
        registeredTools.add(
            McpToolDefinition(
                name = "terminal/run",
                description = "Execute allowlisted shell command in sandbox",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("command", JSONObject().put("type", "string"))
                    })
                    put("required", JSONArray().put("command"))
                }
            )
        )
    }

    private fun registerDefaultMcpResources() {
        registeredResources.add(
            McpResourceDefinition(
                uri = "workspace://project/manifest",
                name = "Project Manifest",
                mimeType = "application/json"
            )
        )
        registeredResources.add(
            McpResourceDefinition(
                uri = "hardware://telemetry/profile",
                name = "Hardware Capability Profile",
                mimeType = "application/json"
            )
        )
    }

    fun handleJsonRpcMessage(rawJson: String): String {
        return try {
            val obj = JSONObject(rawJson)
            val id = obj.optLong("id", -1L)
            val method = obj.optString("method", "")

            when (method) {
                "initialize" -> {
                    isInitialized = true
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", JSONObject().apply {
                            put("protocolVersion", "2024-11-05")
                            put("serverInfo", JSONObject().apply {
                                put("name", serverName)
                                put("version", "2.0.0")
                            })
                            put("capabilities", JSONObject().apply {
                                put("tools", JSONObject())
                                put("resources", JSONObject())
                            })
                        })
                    }.toString()
                }
                "tools/list" -> {
                    val toolsArray = JSONArray()
                    for (tool in registeredTools) {
                        toolsArray.put(JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        })
                    }
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", JSONObject().put("tools", toolsArray))
                    }.toString()
                }
                "tools/call" -> {
                    val params = obj.optJSONObject("params")
                    val toolName = params?.optString("name", "")
                    val args = params?.optJSONObject("arguments")

                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", JSONObject().apply {
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", "Tool '$toolName' executed successfully with args: $args")
                                })
                            })
                        })
                    }.toString()
                }
                "resources/list" -> {
                    val resArray = JSONArray()
                    for (res in registeredResources) {
                        resArray.put(JSONObject().apply {
                            put("uri", res.uri)
                            put("name", res.name)
                            put("mimeType", res.mimeType)
                        })
                    }
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("result", JSONObject().put("resources", resArray))
                    }.toString()
                }
                else -> {
                    JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        put("error", JSONObject().apply {
                            put("code", -32601)
                            put("message", "Method not found: $method")
                        })
                    }.toString()
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", null)
                put("error", JSONObject().apply {
                    put("code", -32700)
                    put("message", "Parse error: ${e.message}")
                })
            }.toString()
        }
    }

    fun listTools(): List<McpToolDefinition> = registeredTools

    fun listResources(): List<McpResourceDefinition> = registeredResources
}
