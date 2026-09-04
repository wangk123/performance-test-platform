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

            ## 二、测试目的与指标

            | 交易 | 指标 | 目标值 | 口径 |
            |---|---|---|---|
            | （示例）查询交易 | TPS | ≥ 200 | 5 分钟均值 |
            | （示例）查询交易 | P95 | ≤ 300 ms | 5 分钟均值 |
            | （示例）查询交易 | 错误率 | ≤ 0.5% | 全量样本 |

            ## 三、测试范围

            ### 范围内交易

            | 交易名称 | 交易配比 | 备注 |
            |---|---|---|
            | （示例）登录 | 30% | |

            ### 范围外清单

            - （列出明确不测的交易及原因）

            ## 四、测试资源

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

            ## 五、测试约束

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

            ## 六、测试策略

            （叙述：压测模型、数据准备策略、监控与观察点。）

            ## 七、场景设计

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

            ## 八、风险与预案

            （列出主要风险与应对。）

            ## 九、排期与协作

            | 环节 | 时间 | 负责人 |
            |---|---|---|
            | 计划评审 |  |  |
            | 脚本编写 |  |  |
            | 执行与观察 |  |  |
            | 报告与发布 |  |  |

            ## 十、附录

            （参考资料、术语等。）

            ## 十一、结论

            ### 指标达成表

            | 指标 | 目标 | 实际结果 | 状态 |
            |---|---|---|---|
            | （示例）查询交易 TPS | ≥ 200 | 待执行 | 待判定 |

            ### 风险与建议

            （发布前填写。）

            **总体结论**：（发布时填写）
            """;

    @Bean
    public ApplicationRunner planTemplateSeed(PersistentPlanTemplateRepository repository) {
        return args -> {
            if (repository.findFirstByBuiltinTrueOrderByIdAsc().isEmpty()) {
                repository.save(new PersistentPlanTemplateRecord(
                        null, "通用压测计划", "内置通用压测计划模板（11 章节固定结构）",
                        BUILTIN_TEMPLATE, true, "system"));
            }
        };
    }
}
