<template>
  <el-dialog
    v-model="scriptImportDialogVisible"
    title="导入 JMeter 脚本"
    width="560px"
    @closed="resetScriptImportForm"
  >
    <div class="script-import-dialog">
      <div class="import-guidance">
        <span class="eyebrow">Import Scope</span>
        <h3>解析后直接生成平台脚本资产</h3>
        <p>导入不会在脚本页展示单独上传记录。系统会提取线程组、API、变量、监控配置和默认执行参数；同名脚本会追加新版本。</p>
      </div>

      <label class="drop-zone" :class="{ 'has-file': scriptFile }">
        <input ref="scriptFileInputRef" type="file" accept=".jmx" @change="handleScriptFileChange" />
        <span>{{ scriptFile ? scriptFile.name : '选择 .jmx 文件' }}</span>
        <small>{{ scriptFile ? formatFileSize(scriptFile.size) : '支持 JMeter JMX 文件，导入完成后弹窗会自动关闭' }}</small>
      </label>

      <el-form class="upload-form" label-position="top" @submit.prevent>
        <el-form-item label="平台脚本名称">
          <el-input v-model="scriptForm.name" placeholder="默认使用 JMX 文件名" />
        </el-form-item>
        <el-form-item label="导入备注">
          <el-input
            v-model="scriptForm.remark"
            type="textarea"
            :rows="3"
            placeholder="例如 调整登录链路线程组"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="scriptImportDialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="scriptUploading"
        :disabled="!scriptFile"
        @click="importScriptAsset"
      >解析并导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { formatFileSize } from '../../utils/format';
import { useScriptImport } from '../../composables/useScriptImport';

const {
  scriptImportDialogVisible,
  scriptUploading,
  scriptFile,
  scriptFileInputRef,
  scriptForm,
  resetScriptImportForm,
  handleScriptFileChange,
  importScriptAsset,
} = useScriptImport();
</script>
