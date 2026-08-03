# AI 可执行命令文档
#
# 本文件由 Kilacraft-AI 自动生成，已内置插件自身的命令。
# 请在此补充服务器上其他插件的命令和自定义命令。
# 修改后执行 /kila reload 即可生效。
#
# 本文件独立于知识库（knowledge/ 目录），仅用于 AI 命令执行技能的命令识别。
#
# 【格式说明】
# 每条命令用 ## 标题声明（不含前导 /），紧跟以下字段：
#   说明: 一句话描述命令的作用
#   示例: 命令的完整调用示例（含参数）
#   权限: 执行该命令所需的服务器权限节点（AI 注入时按玩家权限过滤，无权命令不展示给 AI）
#   关键词: 玩家可能用来表达这个意图的词语，逗号分隔（可选，提高识别准确率）
#
# 【示例】
# ## home
# 说明: 回到已设置的家
# 示例: home [家名]
# 权限: essentials.home
# 关键词: 回家, 回出生点
#
# ============ 以下为 Kilacraft-AI 插件内置命令 ============

## kila clear
说明: 清除自己的 AI 对话历史
示例: kila clear
权限: kilacraft.clear.self
关键词: 清除对话, 清空聊天记录, 重置对话, 重新开始

## kila clear <玩家名>
说明: 清除指定玩家的 AI 对话历史（需要管理员权限）
示例: kila clear Steve
权限: kilacraft.clear.other
关键词: 清除其他人的对话, 清空别人的聊天记录

## kila usage
说明: 查看自己的 AI 用量统计
示例: kila usage
权限: kilacraft.query.self
关键词: 用量, 使用统计, 消耗, 花了多少钱

## kila history
说明: 查看自己的 AI 对话历史
示例: kila history
权限: kilacraft.query.self
关键词: 对话历史, 聊天记录, 之前说了什么

## kila memory
说明: 查看自己的玩家画像和 AI 记忆
示例: kila memory
权限: kilacraft.query.self
关键词: 我的画像, AI记忆, 你记住我什么了, 了解我

## kila tasks
说明: 查看定时任务运行状态（需要管理员权限）
示例: kila tasks
权限: kilacraft.tasks
关键词: 定时任务, 任务状态, 后台任务

## kila reload
说明: 重载 Kilacraft-AI 插件配置（需要管理员权限）
示例: kila reload
权限: kilacraft.reload
关键词: 重载, 刷新配置, 重新加载

## kila doctor
说明: 插件运行状态自检（需要管理员权限）
示例: kila doctor
权限: kilacraft.admin.info
关键词: 自检, 诊断, 检查状态, 运行状况

## kila about
说明: 查看插件版本和更新信息（需要管理员权限）
示例: kila about
权限: kilacraft.admin.info
关键词: 版本, 关于, 更新, 最新版

## kila cache
说明: 查看 AI 缓存命中率统计（需要管理员权限）
示例: kila cache
权限: kilacraft.admin.cache
关键词: 缓存命中率, 缓存统计, 命中情况

## kila cache reset
说明: 重置缓存命中率统计计数（需要管理员权限）
示例: kila cache reset
权限: kilacraft.admin.cache
关键词: 重置缓存统计, 清空命中率

## kila knowledge reload
说明: 知识库重新加载（需要管理员权限）
示例: kila knowledge reload
权限: kilacraft.knowledge
关键词: 重载知识库, 刷新知识库

## kila personalities reload
说明: 人格配置重新加载（需要管理员权限）
示例: kila personalities reload
权限: kilacraft.personalities
关键词: 重载人格, 刷新人格配置

## kila notify test
说明: 测试外部通知渠道是否正常（需要管理员权限）
示例: kila notify test
权限: kilacraft.admin.health
关键词: 测试通知, 通知测试

## kila profile start
说明: 开始手动性能采样（需要管理员权限）
示例: kila profile start
权限: kilacraft.admin.health
关键词: 性能采样, 开始采样

## kila profile stop
说明: 停止手动性能采样并生成报告（需要管理员权限）
示例: kila profile stop
权限: kilacraft.admin.health
关键词: 停止采样, 结束采样

## kila profile status
说明: 查看手动性能采样的当前状态（需要管理员权限）
示例: kila profile status
权限: kilacraft.admin.health
关键词: 采样状态

# ============ 以上为内置命令，以下请自行补充 ============
#
# ## home
# 说明: 回到已设置的家
# 示例: home [家名]
# 权限: essentials.home
# 关键词: 回家, 回出生点
#
# ## tpa
# 说明: 请求传送到指定玩家身边
# 示例: tpa 玩家名
# 权限: essentials.tpa
# 关键词: 传送, 找玩家
