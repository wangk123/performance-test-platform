export type JmeterBuiltinFunction = {
  key: string;
  displayName: string;
  category: string;
  description: string;
  example: string;
};

export const jmeterBuiltinFunctions: JmeterBuiltinFunction[] = [
  {
    key: '__UUID',
    displayName: 'UUID',
    category: 'General',
    description: '生成 UUID',
    example: '${__UUID()}',
  },
  {
    key: '__time',
    displayName: '当前时间',
    category: 'General',
    description: '返回当前时间戳或格式化时间',
    example: '${__time(yyyy-MM-dd HH:mm:ss,)}',
  },
  {
    key: '__Random',
    displayName: '随机数',
    category: 'General',
    description: '生成指定范围内的随机整数',
    example: '${__Random(1,100,)}',
  },
  {
    key: '__threadNum',
    displayName: '线程号',
    category: 'General',
    description: '当前线程编号',
    example: '${__threadNum}',
  },
  {
    key: '__V',
    displayName: '变量间接引用',
    category: 'Variables',
    description: '按名称间接读取变量',
    example: '${__V(varName,)}',
  },
  {
    key: '__P',
    displayName: '属性（带默认）',
    category: 'Properties',
    description: '读取 JMeter 属性，可带默认值',
    example: '${__P(propName,)}',
  },
  {
    key: '__property',
    displayName: '属性',
    category: 'Properties',
    description: '读取或设置 JMeter 属性',
    example: '${__property(propName,,,)}',
  },
  {
    key: '__eval',
    displayName: '二次求值',
    category: 'Variables',
    description: '对变量内容再次求值',
    example: '${__eval(${varName})}',
  },
  {
    key: '__urlencode',
    displayName: 'URL Encode',
    category: 'Encoding',
    description: 'URL 编码',
    example: '${__urlencode(value)}',
  },
  {
    key: '__urldecode',
    displayName: 'URL Decode',
    category: 'Encoding',
    description: 'URL 解码',
    example: '${__urldecode(value)}',
  },
  {
    key: '__Base64Encode',
    displayName: 'Base64 Encode',
    category: 'Encoding',
    description: 'Base64 编码',
    example: '${__Base64Encode(value,)}',
  },
  {
    key: '__Base64Decode',
    displayName: 'Base64 Decode',
    category: 'Encoding',
    description: 'Base64 解码',
    example: '${__Base64Decode(value,)}',
  },
  {
    key: '__StringFromFile',
    displayName: '从文件读字符串',
    category: 'Files',
    description: '按行从文件读取字符串',
    example: '${__StringFromFile(path,,,)}',
  },
];
