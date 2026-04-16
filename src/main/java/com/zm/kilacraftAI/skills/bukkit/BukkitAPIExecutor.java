package com.zm.kilacraftAI.skills.bukkit;

import com.zm.kilacraftAI.KilacraftAI;
import com.zm.kilacraftAI.compat.folia.FoliaCompat;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Bukkit API 执行器
 *
 * <p>使用反射动态调用 Bukkit API</p>
 *
 * @author Zm_Mmm
 * @since 2026-04-01
 */
public class BukkitAPIExecutor {

    /**
     * 当前执行上下文中的玩家引用，用于为需要 Location 参数的方法提供默认值
     */
    private Player currentPlayer;

    /**
     * 执行 API 调用
     *
     * @param api      API 元数据
     * @param player   目标玩家
     * @param entities LLM 提取的参数（暂未使用，预留扩展）
     * @return 执行结果
     */
    public Object execute(BukkitAPIMetadata api, Player player, Map<String, String> entities) throws Exception {
        // TODO: 未来版本将支持从 entities 中提取参数传递给方法
        // 当前版本忽略 entities 参数，只支持无参数方法调用

        // 保存玩家引用，供方法链中的默认参数使用
        this.currentPlayer = player;

        // 权限检查
        if (api.getRequiredPermission() != null && !api.getRequiredPermission().isEmpty()) {
            if (player == null || !player.hasPermission(api.getRequiredPermission())) {
                throw new IllegalStateException("你没有权限执行此操作：" + api.getRequiredPermission());
            }
        }

        // 获取目标对象
        Object target = getTargetObject(api.getTargetType(), player);

        if (target == null) {
            throw new IllegalStateException("无法获取目标对象：" + api.getTargetType());
        }

        // 判断使用哪种模式
        Object result;
        if (api.getMethodChain() != null && !api.getMethodChain().isEmpty()) {
            // 模式 1：method_chain - 链式调用返回复杂对象
            result = executeMethodChain(target, api.getMethodChain());
        } else if (api.getAdditionalMethods() != null && !api.getAdditionalMethods().isEmpty()) {
            // 模式 2：additional_methods - 并行调用多个独立方法
            result = executeAdditionalMethods(target, api.getAdditionalMethods());
        } else {
            throw new IllegalStateException("API 必须配置 method_chain 或 additional_methods");
        }
        
        // Folia 端特殊处理：
        // - 通过 invokeOnMainThread 调用的方法已经在区域线程内提取为 Map
        // - 只有未调度到区域线程的方法（不在 MAIN_THREAD_METHODS 中）才需要在此提取
        if (FoliaCompat.isFolia()) {
            return extractThreadSafeData(result, api.getId());
        }
        return result;
    }

    /**
     * 执行方法链（用于 method_chain）
     */
    private Object executeMethodChain(Object target, java.util.List<String> methodChain) throws Exception {
        Object result = target;
        for (String methodName : methodChain) {
            result = invokeMethod(result, methodName);

            // 如果中间结果为 null，提前结束
            if (result == null) {
                break;
            }
        }

        return result;
    }

    /**
     * 执行额外方法集合（用于 additional_methods）
     */
    private Map<String, Object> executeAdditionalMethods(Object target, Map<String, String> additionalMethods) throws Exception {
        Map<String, Object> results = new java.util.HashMap<>();

        for (Map.Entry<String, String> entry : additionalMethods.entrySet()) {
            String placeholderName = entry.getKey();
            String methodName = entry.getValue();

            // 支持简单链式调用（如 "getLocation.getX"）
            Object value;
            if (methodName.contains(".")) {
                value = executeSimpleMethodChain(target, methodName);
            } else {
                value = invokeMethod(target, methodName);
            }

            results.put(placeholderName, value);
        }

        return results;
    }

    /**
     * 执行简单的两层方法链（用于 additional_methods 中的链式调用）
     */
    private Object executeSimpleMethodChain(Object target, String methodChain) throws Exception {
        String[] methods = methodChain.split("\\.");
        Object current = target;

        for (String method : methods) {
            current = invokeMethod(current, method);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    /**
     * 获取目标对象
     */
    private Object getTargetObject(String targetType, Player player) {
        return switch (targetType) {
            case "Player" -> player;
            case "World" -> player != null ? player.getWorld() : null;
            case "Server" -> player != null ? player.getServer() : null;
            default -> null;
        };
    }

    /**
     * 需要在主线程执行的方法名集合
     * 这些方法涉及 Chunk/Entity/World 操作，在异步线程调用会触发 NPE 或 AsyncCatcher 异常
     * - getLivingEntities / getEntities：遍历 Chunk 中的实体
     * - getTargetBlock：射线追踪访问 BlockState（lophine 等端要求主线程，Spigot 端不强制但也不安全）
     * - getBiome / getTemperature / getHumidity：通过坐标访问 Chunk 中的生物群系/气候数据
     */
    private static final java.util.Set<String> MAIN_THREAD_METHODS = java.util.Set.of("getLivingEntities", "getEntities", "getTargetBlock", "getBiome", "getTemperature", "getHumidity");

    /**
     * 反射调用方法
     */
    private Object invokeMethod(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }

        Class<?> clazz = target.getClass();

        // 查找方法
        Method method = findMethod(clazz, methodName);

        if (method == null) {
            throw new NoSuchMethodException("方法不存在：" + methodName + " in " + clazz.getName());
        }

        // 设置可访问
        method.setAccessible(true);

        // 检查是否需要在主线程执行（Folia 下始终需要调度，因为无全局主线程）
        if (MAIN_THREAD_METHODS.contains(methodName) && !FoliaCompat.isPrimaryThread()) {
            return invokeOnMainThread(target, method);
        }

        // 调用方法（无参数版本，或带默认参数的特殊方法）
        return invokeMethodWithFallback(target, method);
    }

    /**
     * 在主线程/全局区域上同步执行方法调用
     * 用于 Chunk/Entity 等必须在主线程访问的 Bukkit API
     * 委托 FoliaCompat 处理 Folia/Spigot 调度差异
     * 
     * <p>lophine/Folia 特殊处理：对于 Player 相关方法，使用 EntityScheduler 而非 GlobalRegionScheduler</p>
     * <p>重要：必须在区域线程内提取线程敏感对象为纯数据，否则跨线程访问会报 getCurrentWorldData() is null</p>
     */
    private Object invokeOnMainThread(Object target, Method method) throws Exception {
        try {
            // 如果 target 是 Player，使用 EntityScheduler（lophine 要求）
            if (target instanceof org.bukkit.entity.Player player) {
                return FoliaCompat.callSyncOnEntity(player, () -> {
                    try {
                        Object result = invokeMethodWithFallback(target, method);
                        // Folia 端：在区域线程内立即提取为线程安全数据
                        if (FoliaCompat.isFolia()) {
                            return extractThreadSafeData(result, method.getName());
                        }
                        return result;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, 5);
            }
            
            // 其他对象使用 GlobalRegionScheduler
            return FoliaCompat.callSync(KilacraftAI.getInstance(), () -> {
                try {
                    Object result = invokeMethodWithFallback(target, method);
                    // Folia 端：在区域线程内立即提取为线程安全数据
                    if (FoliaCompat.isFolia()) {
                        return extractThreadSafeData(result, method.getName());
                    }
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 5);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    /**
     * 将线程敏感对象提取为线程安全的数据结构
     * 
     * <p>lophine/Folia 要求：Block、Biome、Location、ItemStack 等对象只能在创建它们的区域线程访问，
     * 跨线程访问会报 getCurrentWorldData() is null。</p>
     * <p>此方法在区域线程内调用，将对象转换为 Map/String 等纯数据。</p>
     * <p>注意：Map 字段名使用标准化命名，与 extractDataFromResult 保持一致，
     * 确保多步骤任务的占位符引用（如 {step_1.item_name}）能正确解析。</p>
     */
    private Object extractThreadSafeData(Object result, String methodName) {
        if (result == null) {
            return null;
        }

        // Block 对象：提取为 Map
        if (result instanceof org.bukkit.block.Block block) {
            Map<String, Object> blockData = new HashMap<>();
            blockData.put("block_type", block.getType().name());
            blockData.put("x", block.getX());
            blockData.put("y", block.getY());
            blockData.put("z", block.getZ());
            blockData.put("world", block.getWorld().getName());
            return blockData;
        }

        // Biome 对象：提取为 String
        if (result instanceof org.bukkit.block.Biome biome) {
            return biome.name();
        }

        // Location 对象：提取为 Map（包含 World 名称）
        if (result instanceof org.bukkit.Location location) {
            Map<String, Object> locData = new HashMap<>();
            locData.put("x", location.getX());
            locData.put("y", location.getY());
            locData.put("z", location.getZ());
            locData.put("yaw", location.getYaw());
            locData.put("pitch", location.getPitch());
            locData.put("world", location.getWorld() != null ? location.getWorld().getName() : "unknown");
            return locData;
        }

        // ItemStack 对象：提取为 Map（包含详细信息）
        if (result instanceof org.bukkit.inventory.ItemStack itemStack) {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("item_type", itemStack.getType().name());
            itemData.put("item_amount", itemStack.getAmount());
            
            // item_name 优先使用自定义名称，否则使用中文翻译
            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                itemData.put("item_name", itemStack.getItemMeta().getDisplayName());
            } else {
                // 使用 ItemTranslator 翻译为中文名
                String chineseName = com.zm.kilacraftAI.translate.ItemTranslator.getInstance()
                    .translateToChinese(itemStack.getType().name());
                itemData.put("item_name", chineseName);
            }
            
            // 提取 ItemMeta 详细信息
            if (itemStack.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
                
                // 1. 附魔列表
                if (meta.hasEnchants()) {
                    Map<String, Integer> enchantments = new HashMap<>();
                    meta.getEnchants().forEach((ench, level) -> 
                        enchantments.put(ench.getName(), level)
                    );
                    itemData.put("enchantments", enchantments);
                }
                
                // 2. 耐久度（损伤值）
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    if (damageable.hasDamage()) {
                        itemData.put("damage", damageable.getDamage());
                        // 计算剩余耐久度
                        int maxDurability = itemStack.getType().getMaxDurability();
                        if (maxDurability > 0) {
                            itemData.put("max_durability", maxDurability);
                            itemData.put("remaining_durability", maxDurability - damageable.getDamage());
                        }
                    }
                    // 是否不可破坏
                    if (meta.isUnbreakable()) {
                        itemData.put("unbreakable", true);
                    }
                }
                
                // 3. Lore（物品描述）
                if (meta.hasLore()) {
                    itemData.put("lore", meta.getLore());
                }
                
                // 4. 属性修饰词（如 +攻击伤害）
                if (meta.hasAttributeModifiers()) {
                    Map<String, Object> attributes = new HashMap<>();
                    meta.getAttributeModifiers().entries().forEach(entry -> {
                        org.bukkit.attribute.Attribute attr = entry.getKey();
                        org.bukkit.attribute.AttributeModifier modifier = entry.getValue();
                        attributes.put(attr.name(), modifier.getAmount());
                    });
                    if (!attributes.isEmpty()) {
                        itemData.put("attributes", attributes);
                    }
                }
                
                // 5. 自定义模型数据
                if (meta.hasCustomModelData()) {
                    itemData.put("custom_model_data", meta.getCustomModelData());
                }
                
                // 6. ItemFlags（隐藏的附魔等信息）
                if (!meta.getItemFlags().isEmpty()) {
                    itemData.put("item_flags", meta.getItemFlags().stream()
                        .map(Enum::name)
                        .toList());
                }
            }
            
            return itemData;
        }

        // Vector 对象：提取为 Map
        if (result instanceof org.bukkit.util.Vector vector) {
            Map<String, Object> vecData = new HashMap<>();
            vecData.put("x", vector.getX());
            vecData.put("y", vector.getY());
            vecData.put("z", vector.getZ());
            return vecData;
        }

        // PotionEffect 对象：提取为 Map
        if (result instanceof org.bukkit.potion.PotionEffect effect) {
            Map<String, Object> effectData = new HashMap<>();
            effectData.put("type", effect.getType().getName());
            effectData.put("amplifier", effect.getAmplifier());
            effectData.put("duration", effect.getDuration());
            return effectData;
        }

        // 其他类型直接返回（String、Integer、List、Enum 等已经是线程安全的）
        return result;
    }

    /**
     * 调用方法，对需要参数的特殊方法提供默认参数
     */
    private Object invokeMethodWithFallback(Object target, Method method) throws Exception {
        if (method.getParameterCount() == 0) {
            return method.invoke(target);
        }

        // 对需要参数的方法，根据方法名提供默认参数
        String methodName = method.getName();
        int paramCount = method.getParameterCount();
        Object[] defaultArgs = buildDefaultArgs(methodName, paramCount);

        if (defaultArgs != null) {
            return method.invoke(target, defaultArgs);
        }

        // 无法提供默认参数，抛出异常
        throw new NoSuchMethodException("方法 " + methodName + " 需要参数但不支持无参调用，且没有配置默认参数");
    }

    /**
     * 查找方法（智能匹配策略 + 类型兼容性检查）
     *
     * <p>策略：
     * 1. 优先无参方法
     * 2. 如果没有无参方法，选择参数最少且 buildDefaultArgs 能提供默认参数、且参数类型兼容的方法
     * 3. 如果都没有默认参数支持，选择参数最少的方法
     * </p>
     *
     * <p>类型兼容性检查解决的核心问题：
     * 高版本 Paper/Spigot 中 getTargetBlock 可能同时有
     * getTargetBlock(Set<Material>, int) — 旧版
     * getTargetBlock(int, FluidCollisionMode) — 新版
     * 两者都是 2 参数，但传入 {null, 100} 只兼容旧版签名。</p>
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        Method bestWithDefaults = null;
        int bestDefaultsParamCount = Integer.MAX_VALUE;
        Method bestFallback = null;
        int bestFallbackParamCount = Integer.MAX_VALUE;

        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(methodName)) continue;

            int pc = method.getParameterCount();

            // 优先：无参方法
            if (pc == 0) {
                return method;
            }

            // 检查 buildDefaultArgs 是否能为此方法提供参数
            Object[] defaultArgs = buildDefaultArgs(methodName, pc);
            if (defaultArgs != null) {
                // 关键：检查默认参数类型与方法参数类型是否兼容
                if (isArgsCompatible(method, defaultArgs)) {
                    if (pc < bestDefaultsParamCount) {
                        bestDefaultsParamCount = pc;
                        bestWithDefaults = method;
                    }
                }
                // 类型不兼容的方法不参与有默认参数的候选，但可参与兜底候选
                else if (pc < bestFallbackParamCount) {
                    bestFallbackParamCount = pc;
                    bestFallback = method;
                }
            } else if (pc < bestFallbackParamCount) {
                // 兜底：参数最少的方法
                bestFallbackParamCount = pc;
                bestFallback = method;
            }
        }

        // 优先返回有默认参数支持且类型兼容的方法
        return bestWithDefaults != null ? bestWithDefaults : bestFallback;
    }

    /**
     * 检查默认参数数组是否与方法的参数类型兼容
     * 处理 null（可赋值给引用类型但不能赋值给原始类型）和自动拆箱（Integer -> int）
     */
    private boolean isArgsCompatible(Method method, Object[] args) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != args.length) return false;

        for (int i = 0; i < args.length; i++) {
            Class<?> paramType = paramTypes[i];
            if (args[i] == null) {
                // null 只能赋值给引用类型，不能赋值给原始类型（int, long 等）
                if (paramType.isPrimitive()) return false;
            } else {
                // 检查参数值类型是否可以赋值给方法参数类型（含自动拆箱）
                if (!isAssignable(paramType, args[i].getClass())) return false;
            }
        }
        return true;
    }

    /**
     * 检查 source 类型是否可以赋值给 target 类型（含自动装箱/拆箱）
     */
    private boolean isAssignable(Class<?> target, Class<?> source) {
        // 直接兼容
        if (target.isAssignableFrom(source)) return true;
        // 基本类型 <-> 包装类型
        if (target == int.class && source == Integer.class) return true;
        if (target == long.class && source == Long.class) return true;
        if (target == double.class && source == Double.class) return true;
        if (target == float.class && source == Float.class) return true;
        if (target == boolean.class && source == Boolean.class) return true;
        if (target == byte.class && source == Byte.class) return true;
        if (target == short.class && source == Short.class) return true;
        if (target == char.class && source == Character.class) return true;
        return false;
    }

    /**
     * 为需要参数的特殊方法构建默认参数
     *
     * <p>支持的签名（根据 Spigot 1.16.5 反编译确认）：</p>
     * <ul>
     *   <li>World.getBiome(int x, int z) — 2个int</li>
     *   <li>World.getBiome(int x, int y, int z) — 3个int</li>
     *   <li>World.getTemperature(int x, int z) — 2个int</li>
     *   <li>World.getTemperature(int x, int y, int z) — 3个int</li>
     *   <li>World.getHumidity(int x, int z) — 2个int</li>
     *   <li>World.getHumidity(int x, int y, int z) — 3个int</li>
     *   <li>LivingEntity.getTargetBlock(Set<Material>, int) — Set + int</li>
     * </ul>
     */
    private Object[] buildDefaultArgs(String methodName, int paramCount) {
        // Player.getTargetBlock(Set<Material>, int)
        // 注意：transparent 参数传 null 表示只有空气被视为透明（射线穿过空气命中第一个实体方块）
        // 传 emptySet() 会导致空气也不透明，直接返回眼睛位置的空气方块
        if ("getTargetBlock".equals(methodName) && paramCount == 2) {
            return new Object[]{null, 100};
        }

        // World.getBiome / getTemperature / getHumidity
        if (("getBiome".equals(methodName) || "getTemperature".equals(methodName) || "getHumidity".equals(methodName)) && currentPlayer != null) {
            int blockX = currentPlayer.getLocation().getBlockX();
            int blockZ = currentPlayer.getLocation().getBlockZ();

            if (paramCount == 2) {
                // getBiome(int x, int z) / getTemperature(int x, int z) / getHumidity(int x, int z)
                return new Object[]{blockX, blockZ};
            }
            if (paramCount == 3) {
                // getBiome(int x, int y, int z) — y 用玩家当前 y
                int blockY = currentPlayer.getLocation().getBlockY();
                return new Object[]{blockX, blockY, blockZ};
            }
            if (paramCount == 1) {
                // 高版本 Spigot: getBiome(Location)
                return new Object[]{currentPlayer.getLocation()};
            }
        }

        return null;
    }
}
