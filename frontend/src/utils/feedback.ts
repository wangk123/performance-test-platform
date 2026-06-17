import { Modal } from 'ant-design-vue';

type ConfirmActionOptions = {
  title: string;
  content: string;
  okText?: string;
  cancelText?: string;
  okType?: 'primary' | 'danger';
};

export function confirmAction(options: ConfirmActionOptions): Promise<void> {
  return new Promise((resolve, reject) => {
    Modal.confirm({
      title: options.title,
      content: options.content,
      okText: options.okText ?? '确定',
      cancelText: options.cancelText ?? '取消',
      okType: options.okType,
      onOk: () => resolve(),
      onCancel: () => reject(new Error('cancel')),
    });
  });
}
