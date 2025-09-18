package top.maplex.fabled.attribute;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.serverct.ersha.AttributePlus;
import org.serverct.ersha.api.component.SubAttribute;
import studio.magemonkey.fabled.Fabled;
import studio.magemonkey.fabled.dynamic.EffectComponent;
import studio.magemonkey.fabled.manager.FabledAttribute;
import studio.magemonkey.fabled.manager.IAttributeManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AttributePlusManager implements IAttributeManager {

    @Getter
    private final Map<String, FabledAttribute> attributes = new LinkedHashMap<>();
    private final Map<String, FabledAttribute> lookup     = new HashMap<>();

    // MC原生属性到AttributePlus属性的映射
    // 例如: "block-break-speed" -> ["破坏速度", "加速破坏"]
    private final Map<String, List<String>> statMappings = new HashMap<>();

    // 技能参数到AttributePlus属性的映射
    // 例如: "damage" -> ["力量", "强化伤害"]
    private final Map<String, List<String>> componentMappings = new HashMap<>();

    public void load() {
        // 清空之前的数据
        attributes.clear();
        lookup.clear();
        statMappings.clear();
        componentMappings.clear();

        // 加载配置中的映射关系
        loadMappings();

        // 读取AttributePlus的属性数据转换为FabledAttribute对象
        ConcurrentHashMap<String, SubAttribute> attributeNameList = AttributePlus.attributeManager.attributeNameList;
        for (Map.Entry<String, SubAttribute> entry : attributeNameList.entrySet()) {
            FabledAttribute fabledAttribute = getFabledAttribute(entry);

            // 添加到属性映射
            attributes.put(fabledAttribute.getKey(), fabledAttribute);
            // 添加key和name的查找映射
            lookup.put(fabledAttribute.getKey(), fabledAttribute);
            lookup.put(fabledAttribute.getName().toLowerCase(), fabledAttribute);
        }
    }

    /**
     * 从配置加载映射关系
     */
    private void loadMappings() {
        // 加载MC原生属性映射
        ConfigurationSection statConfig = Fabled.inst().getConfig().getConfigurationSection("Classes.attributes");
        if (statConfig != null) {
            for (String statKey : statConfig.getKeys(false)) {
                List<String> attrNames = statConfig.getStringList(statKey);
                if (!attrNames.isEmpty()) {
                    statMappings.put(statKey.toLowerCase(), attrNames);
                }
            }
        }

        // 技能参数映射可以从另一个配置节点加载
        ConfigurationSection skillConfig =
                Fabled.inst().getConfig().getConfigurationSection("Classes.skill-attributes");
        if (skillConfig != null) {
            for (String paramKey : skillConfig.getKeys(false)) {
                List<String> attrNames = skillConfig.getStringList(paramKey);
                if (!attrNames.isEmpty()) {
                    componentMappings.put(paramKey.toLowerCase(), attrNames);
                }
            }
        }
    }

    @NotNull
    private static FabledAttribute getFabledAttribute(Map.Entry<String, SubAttribute> entry) {
        String       key   = entry.getKey();
        SubAttribute value = entry.getValue();

        // 创建FabledAttribute对象
        FabledAttribute fabledAttribute = new FabledAttribute();
        fabledAttribute.setKey(key.toLowerCase());
        fabledAttribute.setDisplay(value.getAttributeName());
        fabledAttribute.setIcon(new ItemStack(Material.PAPER));
        fabledAttribute.setMax(Integer.MAX_VALUE);
        fabledAttribute.setCostBase(1);
        fabledAttribute.setCostModifier(1);
        return fabledAttribute;
    }

    @Override
    public FabledAttribute getAttribute(String key) {
        return lookup.get(key.toLowerCase());
    }

    @Override
    public List<FabledAttribute> forStat(String key) {
        // 用于MC原生属性的映射
        if (key == null) return null;

        String       statKey     = key.toLowerCase();
        List<String> mappedAttrs = statMappings.get(statKey);

        if (mappedAttrs != null && !mappedAttrs.isEmpty()) {
            List<FabledAttribute> result = new ArrayList<>();
            for (String attrName : mappedAttrs) {
                // 通过属性名查找FabledAttribute
                FabledAttribute attr = lookup.get(attrName.toLowerCase());
                if (attr != null) {
                    result.add(attr);
                }
            }
            return result.isEmpty() ? null : result;
        }

        // 如果没有映射，尝试直接查找
        FabledAttribute directMatch = lookup.get(statKey);
        return directMatch != null ? Collections.singletonList(directMatch) : null;
    }

    @Override
    public List<FabledAttribute> forComponent(EffectComponent component, String key) {
        // 用于技能参数的映射
        if (key == null) return null;

        // 处理组件-参数格式，如 "damage-value" -> "value"
        String paramKey = key.toLowerCase();
        if (paramKey.contains("-")) {
            String[] parts = paramKey.split("-");
            if (parts.length > 1) {
                paramKey = parts[1]; // 获取参数名部分（value, range, duration等）
            }
        }

        List<String> mappedAttrs = componentMappings.get(paramKey);

        if (mappedAttrs != null && !mappedAttrs.isEmpty()) {
            List<FabledAttribute> result = new ArrayList<>();
            for (String attrName : mappedAttrs) {
                FabledAttribute attr = lookup.get(attrName.toLowerCase());
                if (attr != null) {
                    result.add(attr);
                }
            }
            return result.isEmpty() ? null : result;
        }

        // 如果没有映射，尝试直接查找
        FabledAttribute directMatch = lookup.get(paramKey);
        return directMatch != null ? Collections.singletonList(directMatch) : null;
    }

    @Override
    public Set<String> getKeys() {
        return attributes.keySet();
    }

    @Override
    public Set<String> getLookupKeys() {
        return lookup.keySet();
    }

    @Override
    public String normalize(String key) {
        final FabledAttribute fabledAttribute = lookup.get(key.toLowerCase());
        if (fabledAttribute == null) {
            throw new IllegalArgumentException("Invalid attribute - " + key);
        }
        return fabledAttribute.getKey();
    }

    @Override
    public void addByComponent(String key, FabledAttribute fabledAttribute) {
        // 当属性支持某个组件参数时，建立反向映射
        // key格式: "component-parameter" (如 "damage-value")
        if (key != null && key.contains("-")) {
            String[] parts = key.split("-");
            if (parts.length > 1) {
                String paramKey = parts[1].toLowerCase(); // 提取参数部分 (如 "value")

                // 获取或创建该参数的属性列表
                List<String> attrNames = componentMappings.computeIfAbsent(paramKey, k -> new ArrayList<>());

                // 添加属性名到映射（如果还没有的话）
                String attrName = fabledAttribute.getName();
                if (!attrNames.contains(attrName)) {
                    attrNames.add(attrName);
                }
            }
        }
    }

    @Override
    public void addByStat(String key, FabledAttribute fabledAttribute) {
        // 当属性支持某个MC原生属性时，建立反向映射
        // key: MC原生属性名 (如 "block-break-speed")
        if (key != null) {
            String statKey = key.toLowerCase();

            // 获取或创建该stat的属性列表
            List<String> attrNames = statMappings.computeIfAbsent(statKey, k -> new ArrayList<>());

            // 添加属性名到映射（如果还没有的话）
            String attrName = fabledAttribute.getName();
            if (!attrNames.contains(attrName)) {
                attrNames.add(attrName);
            }
        }
    }
}
