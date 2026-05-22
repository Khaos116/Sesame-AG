package io.github.aoguai.sesameag.model.modelFieldExt

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.Toast
import io.github.aoguai.sesameag.entity.friend.FriendCapabilityState
import io.github.aoguai.sesameag.entity.friend.FriendCapabilityFilter
import io.github.aoguai.sesameag.entity.friend.FriendCenterConfig
import io.github.aoguai.sesameag.entity.friend.FriendRelationFilter
import io.github.aoguai.sesameag.entity.friend.FriendSelectionCountSpec
import io.github.aoguai.sesameag.entity.friend.FriendSelectionSpec
import io.github.aoguai.sesameag.model.ModelField
import io.github.aoguai.sesameag.util.JsonUtil
import io.github.aoguai.sesameag.util.Log
import io.github.aoguai.sesameag.util.friend.FriendRepository
import io.github.aoguai.sesameag.util.friend.FriendSelectionResolver

open class FriendSelectionModelField(
    code: String,
    name: String,
    value: FriendSelectionSpec = FriendSelectionSpec()
) : ModelField<FriendSelectionSpec>(code, name, value) {

    override fun getType(): String = "FRIEND_SELECTION"

    override fun getEditorMeta(): Any = FriendSelectionEditorMeta(countEnabled = false)

    override fun setObjectValue(objectValue: Any?) {
        value = normalizeSpec(objectValue)
    }

    override fun setConfigValue(configValue: String?) {
        if (configValue.isNullOrBlank()) {
            reset()
            return
        }
        value = try {
            sanitizeSpec(JsonUtil.parseObject(configValue, FriendSelectionSpec::class.java))
        } catch (t: Throwable) {
            Log.runtime("FriendSelectionModelField", "好友选择配置[$code]格式无效，已重置为空")
            FriendSelectionSpec()
        }
    }

    override fun getConfigValue(): String? {
        value = value?.let { sanitizeSpec(it) }
        return super.getConfigValue()
    }

    override fun getView(context: Context): View {
        return Button(context).apply {
            text = name
            isAllCaps = false
            setOnClickListener {
                Toast.makeText(context, "请在 Web 设置页编辑好友分组选择", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun contains(userId: String?): Boolean = FriendSelectionResolver.contains(value, userId)

    fun containsConfigured(userId: String?): Boolean = FriendSelectionResolver.containsConfigured(value, userId)

    fun resolvedIds(): Set<String> = FriendSelectionResolver.resolveIds(value)

    fun isEmpty(): Boolean = resolvedIds().isEmpty()

    fun sanitizeForUser(userId: String?) {
        sanitizeForUser(userId, null)
    }

    fun sanitizeForUser(userId: String?, capabilityModuleKey: String?) {
        value = value?.let { sanitizeSpec(it, userId, capabilityModuleKey) }
    }

    private fun normalizeSpec(raw: Any?): FriendSelectionSpec {
        return sanitizeSpec(when (raw) {
            null -> FriendSelectionSpec()
            is FriendSelectionSpec -> raw
            else -> try {
                JsonUtil.parseObject(raw, FriendSelectionSpec::class.java)
            } catch (_: Throwable) {
                Log.runtime("FriendSelectionModelField", "好友选择配置[$code]格式无效，已重置为空")
                FriendSelectionSpec()
            }
        })
    }
}

class FriendSelectionCountModelField(
    code: String,
    name: String,
    value: FriendSelectionCountSpec = FriendSelectionCountSpec()
) : ModelField<FriendSelectionCountSpec>(code, name, value) {

    override fun getType(): String = "FRIEND_SELECTION_COUNT"

    override fun getEditorMeta(): Any = FriendSelectionEditorMeta(countEnabled = true)

    override fun setObjectValue(objectValue: Any?) {
        value = normalizeSpec(objectValue)
    }

    override fun setConfigValue(configValue: String?) {
        if (configValue.isNullOrBlank()) {
            reset()
            return
        }
        value = try {
            sanitizeCountSpec(JsonUtil.parseObject(configValue, FriendSelectionCountSpec::class.java))
        } catch (t: Throwable) {
            Log.runtime("FriendSelectionCountModelField", "好友计数选择配置[$code]格式无效，已重置为空")
            FriendSelectionCountSpec()
        }
    }

    override fun getConfigValue(): String? {
        value = value?.let { sanitizeCountSpec(it) }
        return super.getConfigValue()
    }

    override fun getView(context: Context): View {
        return Button(context).apply {
            text = name
            isAllCaps = false
            setOnClickListener {
                Toast.makeText(context, "请在 Web 设置页编辑好友分组选择", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun contains(userId: String?): Boolean = FriendSelectionResolver.resolveCountMap(value).containsKey(userId?.trim())

    fun resolvedCountMap(): Map<String, Int> = FriendSelectionResolver.resolveCountMap(value)

    fun isEmpty(): Boolean = resolvedCountMap().isEmpty()

    fun sanitizeForUser(userId: String?) {
        sanitizeForUser(userId, null)
    }

    fun sanitizeForUser(userId: String?, capabilityModuleKey: String?) {
        value = value?.let { sanitizeCountSpec(it, userId, capabilityModuleKey) }
    }

    private fun normalizeSpec(raw: Any?): FriendSelectionCountSpec {
        return sanitizeCountSpec(when (raw) {
            null -> FriendSelectionCountSpec()
            is FriendSelectionCountSpec -> raw
            else -> try {
                JsonUtil.parseObject(raw, FriendSelectionCountSpec::class.java)
            } catch (_: Throwable) {
                Log.runtime("FriendSelectionCountModelField", "好友计数选择配置[$code]格式无效，已重置为空")
                FriendSelectionCountSpec()
            }
        })
    }
}

data class FriendSelectionEditorMeta(
    val kind: String = "FRIEND_SELECTION",
    val countEnabled: Boolean,
    val relationDefault: FriendRelationFilter = FriendRelationFilter.MUTUAL_ONLY
)

private val MANDATORY_CAPABILITY_MODULE_KEYS = linkedSetOf(
    "FARM",
    "FOREST",
    "STALL",
    "DODO",
    "OCEAN",
    "SPORTS"
)

private fun sanitizeSpec(
    spec: FriendSelectionSpec,
    userId: String? = null,
    capabilityModuleKey: String? = null
): FriendSelectionSpec {
    val friendConfig = resolveFriendConfig(userId)
    val knownUserIds = friendConfig?.profiles?.keys.orEmpty()
    val knownGroupIds = friendConfig?.groups?.mapTo(linkedSetOf<String>()) { it.id }.orEmpty()
    val shouldPruneUsers = friendConfig?.userId?.isNotBlank() == true && knownUserIds.isNotEmpty()
    val shouldPruneGroups = friendConfig?.userId?.isNotBlank() == true
    val normalizedCapabilityKey = capabilityModuleKey
        ?.trim()
        ?.uppercase()
        ?.takeIf { MANDATORY_CAPABILITY_MODULE_KEYS.contains(it) }
    val mandatoryCapabilityFilter = normalizedCapabilityKey?.let { moduleKey ->
        FriendCapabilityFilter(
            moduleKeys = linkedSetOf(moduleKey),
            requiredStates = linkedSetOf(FriendCapabilityState.OPEN),
            includeUnknown = true
        )
    }
    return FriendSelectionSpec(
        includeUserIds = sanitizeStringSet(spec.includeUserIds, knownUserIds.takeIf { shouldPruneUsers }),
        includeGroupIds = sanitizeStringSet(spec.includeGroupIds, knownGroupIds.takeIf { shouldPruneGroups }),
        excludeUserIds = sanitizeStringSet(spec.excludeUserIds, knownUserIds.takeIf { shouldPruneUsers }),
        excludeGroupIds = sanitizeStringSet(spec.excludeGroupIds, knownGroupIds.takeIf { shouldPruneGroups }),
        relationFilter = spec.relationFilter,
        capabilityFilter = mandatoryCapabilityFilter
    )
}

private fun sanitizeCountSpec(
    spec: FriendSelectionCountSpec,
    userId: String? = null,
    capabilityModuleKey: String? = null
): FriendSelectionCountSpec {
    val selection = sanitizeSpec(spec.selection, userId, capabilityModuleKey)
    val friendConfig = resolveFriendConfig(userId)
    val canResolveGroups = friendConfig?.userId?.isNotBlank() == true
    val groups = if (canResolveGroups) friendConfig?.groups.orEmpty() else emptyList()
    val validUserIds = linkedSetOf<String>().apply {
        addAll(selection.includeUserIds)
        if (canResolveGroups) {
            selection.includeGroupIds.forEach { groupId ->
                groups.firstOrNull { it.id == groupId }?.memberIds?.let { addAll(it) }
            }
        }
    }
    val validGroupIds = selection.includeGroupIds.toSet()
    return FriendSelectionCountSpec(
        selection = selection,
        defaultCount = spec.defaultCount,
        groupCountOverrides = sanitizeCountMap(
            spec.groupCountOverrides,
            validGroupIds.takeIf { canResolveGroups }
        ),
        userCountOverrides = sanitizeCountMap(
            spec.userCountOverrides,
            validUserIds.takeIf { canResolveGroups }
        )
    )
}

private fun sanitizeStringSet(values: Set<String?>?, allowedValues: Set<String>? = null): LinkedHashSet<String> {
    return linkedSetOf<String>().apply {
        values.orEmpty().mapNotNullTo(this) { rawValue ->
            rawValue?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { allowedValues == null || allowedValues.contains(it) }
        }
    }
}

private fun sanitizeCountMap(values: Map<*, *>?, allowedKeys: Set<String>?): LinkedHashMap<String, Int> {
    return linkedMapOf<String, Int>().apply {
        values.orEmpty().forEach { (rawKey, rawCount) ->
            val key = rawKey?.toString()?.trim().orEmpty()
            val count = when (rawCount) {
                is Number -> rawCount.toInt()
                is String -> rawCount.trim().toIntOrNull()
                else -> null
            }
            if (key.isNotEmpty() && count != null && (allowedKeys == null || allowedKeys.contains(key))) {
                put(key, count)
            }
        }
    }
}

private fun resolveFriendConfig(userId: String?): FriendCenterConfig? {
    val safeUserId = userId?.trim().orEmpty()
    if (safeUserId.isEmpty()) {
        return null
    }
    return runCatching {
        FriendRepository.current(safeUserId)
    }.getOrNull()
}
