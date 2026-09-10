package com.yr.perftest.platform.task.plandoc;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 内置模板 seed：存在即跳过，不可编辑删除（设计 §7.1）。 */
@Configuration
public class PlanTemplateSeeder {

    public static final String BUILTIN_TEMPLATE = """
            # {{planName}} 性能测试计划

            ## 一、背景

            （简述被测系统、本次压测的业务背景与动因。）

            ## 二、测试目的

            （叙述本次压测要达成的业务与技术目的。）

            ## 三、测试指标

            | 对象 | 指标 | 目标值 | 口径 |
            |---|---|---|---|
            | （示例）查询交易 | TPS | ≥ 200 | 5 分钟均值 |
            | （示例）查询交易 | P95 | ≤ 300 ms | 5 分钟均值 |
            | （示例）查询交易 | 错误率 | ≤ 0.5% | 全量样本 |

            ## 四、测试范围

            - （列出纳入本次压测的交易、接口或链路；不在范围内的项一并注明）

            ## 五、测试资源

            ### 人员

            | 角色 | 姓名 | 职责 |
            |---|---|---|
            | 测试负责 |  | 计划与结论 |
            | 执行 |  | 场景执行与观察 |

            ### 环境部署信息

            | 地址 | 模块 | 配置/版本 |
            |---|---|---|
            |  |  |  |

            ### 执行节点与监控目标

            - 执行节点：
            - 监控目标：

            ### 时间窗口

            - 计划执行时间：

            ## 六、测试约束

            ### 入口准则

            - [ ] 指标已定义（自动）
            - [ ] 场景已配置（自动）
            - [ ] 脚本已关联（自动）
            - [ ] 环境就绪（人工）
            - [ ] 数据就绪（人工）
            - [ ] 人员到位（人工）
            - [ ] 接口人明确（人工）

            ### 出口准则

            - [ ] 全部场景按计划执行完成（人工）
            - [ ] 指标达成表已确认（人工）
            - [ ] 风险与建议已记录（人工）

            ## 七、测试策略

            （叙述：压测模型、数据准备策略、监控与观察点。）

            ## 八、场景设计

            （场景块由平台按场景实体生成并回写；示例结构如下，勿手改标记行。）

            ### S1 示例场景 · SINGLE_TXN

            **场景目的**：（填写）

            **测试方法**：（自由编辑，实体同步不触碰此处）

            **交易范围**：（自由编辑，实体同步不触碰此处）

            **场景设置**（由场景执行配置生成，勿手改）：

            | 用户数 | 持续时长 | 加载方式 | 退出方式 |
            |---|---|---|---|
            | 50 | 300 秒 | 匀速加载 30 秒 | 同时退出 |

            #### 执行记录

            ## 九、风险与预案

            （列出主要风险与应对。）

            ## 十、排期与协作

            | 环节 | 时间 | 负责人 |
            |---|---|---|
            | 计划评审 |  |  |
            | 脚本编写 |  |  |
            | 执行与观察 |  |  |
            | 报告与发布 |  |  |

            ## 十一、附录

            （参考资料、术语等。）

            ## 十二、结论

            ### 指标达成表

            （报告生成时按「三、测试指标」自动重绘；无指标计划本表保持为实测记录。）

            | 对象 | 指标 | 目标 | 实际 | 状态 | 说明 |
            |---|---|---|---|---|---|
            | （示例）查询交易 | TPS | ≥ 200 | 待执行 | 待判定 | |

            ### 风险与建议

            （发布前填写。）

            **总体结论**：（发布时填写）
            """;

    public static final String TEMPLATE_DESCRIPTION = "内置通用压测计划模板（12 章节固定结构）";

    @Bean
    public ApplicationRunner planTemplateSeed(PersistentPlanTemplateRepository repository) {
        return args -> {
            PersistentPlanTemplateRecord builtin = repository.findFirstByBuiltinTrueOrderByIdAsc().orElse(null);
            if (builtin == null) {
                repository.save(new PersistentPlanTemplateRecord(
                        null, "通用压测计划", TEMPLATE_DESCRIPTION, BUILTIN_TEMPLATE, true, "system"));
            } else if (!BUILTIN_TEMPLATE.equals(builtin.getContent())) {
                // 内置模板行归平台所有（不可编辑删除）：内容与代码模板不一致时一律就地刷新
                builtin.update("通用压测计划", TEMPLATE_DESCRIPTION, BUILTIN_TEMPLATE);
                repository.save(builtin);
            }
        };
    }
}
