<template>
  <el-dialog
    v-model="editor.stepDialogVisible.value"
    class="step-create-dialog"
    :title="editor.stepDialogTitle.value"
    width="640px"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item v-if="editor.stepDialogForm.value.relation !== 'root'" label="步骤类型">
        <div class="step-type-picker">
          <button
            v-for="option in editor.availableStepTypeOptions.value"
            :key="option.value"
            class="step-type-card"
            :class="[
              { active: editor.stepDialogForm.value.type === option.value },
              `tone-${stepTypeMeta[option.value].tone}`,
            ]"
            type="button"
            @click="editor.stepDialogForm.value.type = option.value"
          >
            <StepTypeIcon :type="option.value" />
            <div>
              <strong>{{ option.label }}</strong>
              <span>{{ stepTypeMeta[option.value].hint }}</span>
            </div>
          </button>
        </div>
      </el-form-item>
      <el-form-item label="步骤名称">
        <el-input v-model.trim="editor.stepDialogForm.value.name" placeholder="默认使用类型名称" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editor.stepDialogVisible.value = false">取消</el-button>
      <el-button type="primary" @click="editor.createStep">创建步骤</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { stepTypeMeta } from '../../constants';
import { useScriptEditor } from '../../composables/useScriptEditor';
import StepTypeIcon from '../scripts/StepTypeIcon.vue';

const editor = useScriptEditor();
</script>
