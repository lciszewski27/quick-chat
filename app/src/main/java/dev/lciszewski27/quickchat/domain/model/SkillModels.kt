package dev.lciszewski27.quickchat.domain.model

import kotlinx.serialization.Serializable

/** A declared secret slot: name + human description. Values live separately. */
@Serializable
data class SecretDecl(
    val name: String,
    val description: String = ""
)

/**
 * A user skill: persisted JavaScript (run via the shared sandbox) exposed to
 * the model as a tool. Only [enabled] + [tested] skills reach the model.
 * Secret *values* are stored separately and never enter tool definitions.
 */
data class Skill(
    val id: String,
    val name: String,
    val toolName: String,
    val description: String,
    val code: String,
    val paramsSchema: String,
    val secrets: List<SecretDecl>,
    val enabled: Boolean,
    val tested: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
