# scenario-dialog

## Purpose

场景创建和编辑弹窗，提供脚本多选、线程组配置预设、执行节点配置和监控目标绑定。

## Requirements

### Requirement: Scenario dialog excludes legacy execution parameters
场景创建/编辑弹窗 SHALL NOT 包含场景级 threads、rampUp、duration、loops 标量字段。

#### Scenario: Create scenario without legacy thread fields
- **WHEN** 用户打开添加场景弹窗
- **THEN** 表单 SHALL 包含场景名称、脚本选择、线程组配置列表、覆盖计划默认配置复选框及执行节点/监控配置
- **AND** 表单 SHALL NOT 包含场景级线程数、Ramp-Up、持续时间、循环次数标量输入

### Requirement: Thread group config editor
场景弹窗 SHALL 支持为场景添加多组线程组配置预设。

#### Scenario: Add thread group preset
- **WHEN** 用户添加线程组配置
- **THEN** 每条配置 SHALL 包含 Thread Group 选择（stepId）、线程数、Ramp-Up、执行时间
- **AND** 配置 SHALL NOT 包含用户自定义名称字段

#### Scenario: Thread group options from script
- **WHEN** 用户选择脚本
- **THEN** Thread Group 下拉 SHALL 列出该脚本中的所有 THREAD_GROUP 步骤

### Requirement: Script list with multi-select and edit
场景弹窗的脚本选择列表 SHALL 支持多选（批量创建场景）并为每个脚本提供编辑按钮。

#### Scenario: Open script editor from scenario dialog
- **WHEN** 用户在场景弹窗脚本列表中点击某个脚本的编辑按钮
- **THEN** 系统 SHALL 在新浏览器标签页中打开脚本编辑页面

#### Scenario: Edit button does not affect script selection
- **WHEN** 用户点击脚本的编辑按钮
- **THEN** 该脚本 SHALL NOT 被选中为场景关联脚本
- **AND** 当前已选中的脚本保持选中状态

#### Scenario: Batch create scenarios
- **WHEN** 用户勾选多个脚本并保存
- **THEN** 系统 SHALL 为每个选中的脚本创建一个场景
