package com.zm.kilacraftAI.skills.bukkit;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
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
        if (api.getMethodChain() != null && !api.getMethodChain().isEmpty()) {
            // 模式 1：method_chain - 链式调用返回复杂对象
            return executeMethodChain(target, api.getMethodChain());
        } else if (api.getAdditionalMethods() != null && !api.getAdditionalMethods().isEmpty()) {
            // 模式 2：additional_methods - 并行调用多个独立方法
            return executeAdditionalMethods(target, api.getAdditionalMethods());
        } else {
            throw new IllegalStateException("API 必须配置 method_chain 或 additional_methods");
        }
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

        // 调用方法（无参数版本）
        return method.invoke(target);
    }

    /**
     * 查找方法
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        // 遍历所有公共方法
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }
}
