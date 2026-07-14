## ADDED Requirements

### Requirement: Scenario dialog excludes execution parameters
场景创建/编辑弹窗 SHALL NOT 包含 threads、rampUp、duration、loops 等执行参数字段，线程配置以脚本中的 ThreadGroup 为唯一数据源。

#### Scenario: Create scenario without thread configuration
- **WHEN** 用户打开添加场景弹窗
- **THEN** 表单 SHALL 包含场景名称、脚本选择、覆盖计划默认配置复选框及执行节点/监控配置
- **AND** 表单 SHALL NOT 包含线程数、Ramp-Up、持续时间、循环次数输入字段

#### Scenario: Edit scenario without thread configuration
- **WHEN** 用户打开编辑场景弹窗
- **THEN** 表单 SHALL 回填场景名称和已选脚本
- **AND** 表单 SHALL NOT 显示或回填线程数、Ramp-Up、持续时间、循环次数

### Requirement: Script list edit button
场景弹窗的脚本选择列表 SHALL 为每个脚本提供编辑按钮，点击后在新标签页打开对应的脚本编辑器页面。

#### Scenario: Open script editor from scenario dialog
- **WHEN** 用户在场景弹窗脚本列表中点击某个脚本的编辑按钮
- **THEN** 系统 SHALL 在新浏览器标签页中打开 `/projects/:projectId/scripts/:scriptId/edit` 脚本编辑页面

#### Scenario: Edit button does not affect script selection
- **WHEN** 用户点击脚本的编辑按钮
- **THEN** 该脚本 SHALL NOT 被选中为场景关联脚本
- **AND** 当前已选中的脚本保持选中状态

### Requirement: Scenario detail removes thread display
场景详情页 SHALL NOT 显示线程数信息。

#### Scenario: Scenario detail without thread count
- **WHEN** 用户查看场景详情
- **THEN** 页面 SHALL 显示场景名称和关联脚本名称
- **AND** 页面 SHALL NOT 显示 `N 线程` 文案
