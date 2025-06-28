# 所有prompt汇总

## 理解需求
需求描述
你是一位经验非常丰富的java web开发工程师。现在你需要开发 ElasticsearchIndexService 独立应用。
ElasticsearchIndexService的具体需求在附件“ElasticsearchIndexService 详细设计文档”中。
请你仔细分析这份文档中的需求。给出开发步骤规划，以及每一步需要完成的工作。在当前步骤完成后请提示我下一步开发内容。

请注意，在本次对话、及后续所有对话中，作均需要遵循以下规则：
1、每一个代码文件在开发时候均要求一次性输出完整代码，尽量不要出现代码文件编写后在文件中预留todo再次更新的情况。
2、在代码开头需要用注释的方式描述代码的目录结构和名称、UTC/GMT+08:00开发时间（精确到秒）、作者、代码用途等基本信息。
3、你所编写的代码均需要遵循开发规范，有清晰的结构和中文注释。对于重要的代码段要有中文注释介绍。
4、你在输出代码需要标明代码文件的目录结构。
5、每个代码文件都是独立文件，请不要将多个代码文件放在一个输出文件中。
6、你的推理、分析、思考、思路、最终的输出都必须使用中文。



## 编写代码
**阶段一：项目设置与核心配置**
* **步骤 1: 项目初始化与依赖管理** - 创建 Spring Boot 项目，配置 Maven 依赖。
* **步骤 2: 配置文件与基础应用结构** - 创建 `application.properties` 和主应用类，定义包结构。
* **步骤 3: Elasticsearch 客户端配置与索引模板** - 配置 ES Java API Client，定义 ES 索引模板。
* **步骤 4: Kafka Topic 配置** - 配置 Kafka Topic 自动创建。

**阶段二：核心索引逻辑 (Kafka 消费者)**
* **步骤 5: 数据传输对象 (DTO) 定义** - 创建 Kafka 消息和 ES 文档对应的 DTO。
* **步骤 6: 文件解析服务** - 实现集成 Apache Tika 的文件内容与元数据提取服务。
* **步骤 7: Elasticsearch 持久化服务** - 实现与 ES 交互的文档增删改服务，包括乐观锁。
* **步骤 8: Kafka 消费者实现** - 实现 Kafka 监听器处理文件新增/更新和删除事件。

**阶段三：历史数据批量处理**
* **步骤 9: Elasticsearch 文档 ID 生成工具类** - 创建统一的文档 ID 生成逻辑。
* **步骤 10: Spring Batch 配置** - 配置 Spring Batch 作业、步骤、读写器和处理器。
* **步骤 11: Spring Batch ItemReader 实现** - 实现扫描目标目录文件的 ItemReader。
* **步骤 12: Spring Batch ItemProcessor 实现** - 实现文件内容处理并转换为 ES 文档的 ItemProcessor。
* **步骤 13: Spring Batch ItemWriter 实现** - 实现将文档批量写入 ES 的 ItemWriter。
* **步骤 14: Batch 作业服务与监听器** - 实现 Batch 作业的启动、监控服务及完成监听器。

**阶段四：监控与控制**
* **步骤 15: Batch 作业管理 Controller** - 实现用于启动和监控 Batch 作业的 REST API。
* **步骤 16: 服务状态与 DLQ 管理 Controller** - 实现用于监控服务状态、Kafka、ES 及 DLQ 的 REST API。
* **步骤 17: 前端监控页面 - HTML 结构与 CSS** - 创建监控页面的基本 HTML 和 CSS。
* **步骤 18: 前端监控页面 - JavaScript 逻辑** - 实现前端与后端 API 交互及动态更新页面的 JS 逻辑。

**阶段五：最终化与测试**
* **步骤 19: 工具类与异常处理** - 实现辅助工具类和自定义异常。
* **步骤 20: 集成测试与优化** - 进行端到端测试，并根据结果进行优化。
* **步骤 21: 文档与打包** - 完善代码注释，准备部署相关内容。
















































































































### 数据维护模块代码开发




































































































#### 阶段一：项目设置与核心配置
##### 步骤 1: 项目初始化与依赖管理。
接下来请你开始对文档中的代码开发进行规划。
请注意，我希望每一个代码文件在开发时候均要求一次性输出完整代码，尽量不要出现代码文件编写后在文件中预留todo再次更新的情况。
请先输出数据库的表结构设计，并为表中制作示例数据。请注意。输出的内容和顺序包括：
1、先输出所有数据库建表语句及索引语句，并在每个表前面用注释说明表的用途;
2、每个表和每个字段都要有comment说明;
3、以注释的方式输出所有表的示例数据;
4、以注释的方式输出所有表的truncate语句;
5、以注释的方式输出所有表的drop table语句.

输出的SQL语句将会保存在src/main/resources/db/filemanage_init.sql文件中。


#### 后端代码开发
##### entity 层
我已经按照你的建议执行filemanage_init.sql的语句创建了所有表结构。并且在application.properties 中增加了目录相关配置
接下来请你开始开发代码工作。
请先输出entity类代码：
创建 FileSyncMap.java，包含 id, relativeDirPath, originalFilename, tempFilename, status, lastUpdated 属性，并添加必要的 getter/setter 和构造函数 (或使用 Lombok)。
Mapper 接口 (org.ls.mapper):
创建 FileSyncMapMapper.java 接口，定义 V8 方案中描述的所有数据库操作方法（insert, updateStatus, deleteById, selectBySourcePath, selectAndLockPending 等）。
Mapper XML (resources/mapper):
创建 FileSyncMapMapper.xml 文件，为 FileSyncMapMapper 接口中的每个方法编写对应的 SQL 语句。特别注意 selectAndLockPending 方法需要使用 PostgreSQL 的行锁机制（如 FOR UPDATE SKIP LOCKED）来处理并发。

请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
你在输出代码需要标明代码文件的目录结构。

##### DTO 层
请你编写对应的DTO 类 (org.ls.dto)代码文件
DTO 类 (org.ls.dto):
创建 DecryptedFileDto.java (用于文件查询结果)。
创建 PendingFileSyncDto.java (用于待同步文件列表)。
创建通用的 PageDto<T> 。
创建 FileSyncStatusDto.java (用于同步状态显示)。
创建 FileSyncTaskControlResultDto.java (用于同步控制操作的返回)。
请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
输出的代码文件独立分开。一个代码文件只包含该代码的内容，多个代码文件使用各自的文件输出。

##### service层 part1
请你基于这些 Mapper 创建对应的 Service 层代码（接口和实现）
创建 FileManagementService.java 接口，定义 searchDecryptedFiles 和 getDecryptedFileResource 方法。
实现 (org.ls.service.impl):
创建 FileManagementServiceImpl.java 实现 FileManagementService。
注入 Environment 获取 target-dir 配置。
实现 searchDecryptedFiles：使用 Java NIO Files.walk 或类似方法递归扫描目标目录，根据 keyword 过滤，手动计算分页，组装 DecryptedFileDto 和 PageDto。注意处理文件属性读取可能发生的异常。
实现 getDecryptedFileResource：构造文件路径，检查文件存在性和可读性，创建 Spring 的 Resource 对象返回。

请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
在service和impl中对数据库的增删改查操作均需要编写对应的方法和实现。
输出的代码文件独立分开。一个代码文件只包含该代码的内容，多个代码文件使用各自的文件输出。

##### service层 Part 2:
请你继续创建 服务层 (Service Layer - Part 2: File Sync Logic) 代码
接口 (org.ls.service):
创建 FileSyncService.java 接口，定义后台监控启动/停止（隐式通过生命周期管理）、获取待同步文件列表 (getPendingSyncFiles)、获取同步状态 (getSyncStatus) 以及手动同步控制的方法 (startManualSync, pauseManualSync, resumeManualSync, stopManualSync)。
实现 (org.ls.service.impl):
创建 FileSyncServiceImpl.java 实现 FileSyncService。
注入 FileSyncMapMapper, Environment, TaskExecutor (需要配置一个 TaskExecutor Bean)。
实现后台监控 (流程 A):
使用 @PostConstruct 和 @PreDestroy (或实现 InitializingBean, DisposableBean, ApplicationListener) 管理监控线程的生命周期。
在初始化方法中，检查 file.sync.enabled 配置，如果启用，则创建 WatchService，递归注册源目录，并启动一个单独的线程 (TaskExecutor.execute()) 来运行事件监听循环。
在事件监听循环中，处理 ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE 事件：
计算路径和文件名。
调用 FileSyncMapMapper 进行数据库查询和更新（确保使用 @Transactional 或编程式事务管理）。
执行文件复制 (Files.copy) 或删除 (Files.deleteIfExists) 操作。
处理 temp_filename 冲突。
实现健壮的错误处理和日志记录。
在销毁方法中，关闭 WatchService 并停止监控线程。
实现查询方法:
getPendingSyncFiles: 调用 Mapper 查询数据库（带分页），获取临时文件属性，组装 DTO 返回。
getSyncStatus: 调用 Mapper 查询计数，返回内部维护的 syncProcessStatus 等状态信息。

请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
在service和impl中对数据库的增删改查操作均需要编写对应的方法和实现。
输出的代码文件独立分开。一个代码文件只包含该代码的内容，多个代码文件使用各自的文件输出。


##### service层 Part 3:
请你继续创建 服务层 (Service Layer - Part 3: Async Sync Control) 代码
接口 (org.ls.service):
在 FileSyncServiceImpl.java 中实现异步控制逻辑:
定义 AtomicReference<Future<?>>, AtomicBoolean pauseFlag, AtomicBoolean cancelFlag, AtomicReference<String> syncProcessStatus 等成员变量。
实现 startManualSync, pauseManualSync, resumeManualSync, stopManualSync 方法，用于更新状态标志和提交/控制异步任务。
创建私有的 @Async 方法 runSyncCycle：
实现核心的同步循环逻辑。
在循环内部，事务性地调用 mapper.selectAndLockPending() 获取并更新一批记录的状态为 syncing。
遍历批次，执行 Files.move()。
根据移动结果，独立事务性地更新单条记录状态为 synced 或 error_syncing。
处理 pauseFlag 和 cancelFlag。
循环结束后更新 syncProcessStatus 为 idle。
确保配置了 @EnableAsync 并定义了 TaskExecutor Bean。

请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
在service和impl中对数据库的增删改查操作均需要编写对应的方法和实现。
输出的代码文件独立分开。一个代码文件只包含该代码的内容，多个代码文件使用各自的文件输出。

##### controller 层
请你继续工作创建 Controller层代码：
页面路由 (org.ls.controller):
在已有的 PageController  中添加 @GetMapping("/filemanage") 方法，返回 "filemanage" 视图名称。
API 端点 (org.ls.controller.api):
创建 FileManageApiController.java。
注入 FileManagementService 和 FileSyncService。
实现 V8 方案中定义的所有 API 端点，调用相应的 Service 方法，并返回 DTO 或 ResponseEntity<Resource>。
/api/filemanage/decrypted/search
/api/filemanage/decrypted/download
/api/filemanage/pending
/api/filemanage/sync/status
/api/filemanage/sync/start
/api/filemanage/sync/pause
/api/filemanage/sync/resume
/api/filemanage/sync/stop

全局异常处理:
检查或增强 GlobalExceptionHandler 以捕获和处理文件管理及同步过程中可能出现的特定异常（如 FileNotFoundException, IOException, 数据库异常等），返回友好的错误信息。

请注意：
在代码开头需要用注释的方式描述代码的目录结构和名称、开发时间（精确到秒）、作者、代码用途等基本信息。
你所编写的代码均需要遵循开发规范，有清晰的结构和注释。
在controller层可以使用utils中的公共代码实现用户输入字符检查等功能、可以使用exception做全局的异常监测
输出的代码文件独立分开。一个代码文件只包含该代码的内容，多个代码文件使用各自的文件输出。


#### 前端代码开发

##### html
请开始前端代码编写工作，先完成html的修改：
HTML (resources/templates/filemanage.html):
创建该文件，设置基本的 HTML 结构。
使用 Thymeleaf 引入公共片段 (header, scripts)。
实现两列布局（侧边栏 + 内容区）。
构建侧边栏导航链接。
构建两个内容区域 (#query-download-section, #sync-management-section)，包含标题、控件（输入框、按钮）、表格容器和分页容器。
确保表格有正确的 id 和 <thead>，<tbody> 用于动态填充。给同步表格的 <tr> 添加 data-id 属性。

##### css
请开始css开发。
CSS (resources/static/css/filemanage.css):
创建该文件。
编写侧边栏布局样式、激活链接样式。
为表格容器 (#decrypted-table-container, #pending-table-container) 设置 max-height 和 overflow-y: auto 以实现内部滚动。
为表格设置 table-layout: fixed; width: 100%; 并定义列宽，防止横向滚动条。
定义 .removing class 的 CSS 动画（例如 opacity, height, transition）。

##### js api
请开始 js 开发的第1部分，先实现 JavaScript 基础与 API 交互
JS 目录 (resources/static/js/filemanage/):
创建该目录。
API 模块 (filemanage_api.js):
创建该文件。
封装所有对 /api/filemanage/* 端点的 fetch 调用，处理基本的请求发送和 Promise 返回。可以包含统一的错误处理逻辑。
UI 模块 (filemanage_ui.js):
创建该文件。
实现 DOM 操作函数：更新表格内容、更新分页、更新状态栏文本、控制按钮状态、添加/移除 CSS 类（用于动画和高亮）。
入口模块 (filemanage_main.js):
创建该文件。
编写 DOMContentLoaded 监听器。
获取主要 DOM 元素的引用。
绑定侧边栏链接的点击事件，调用 filemanage_ui.js 中的函数切换内容区域显示。
在初始化时调用后续模块的加载函数。
设置定时器调用 updateSyncStatus()。

##### js 功能
请开始 js 开发的第2部分，功能实现 (JavaScript)
查询下载模块 (filemanage_query.js):
创建该文件。
实现 loadDecryptedFiles(page) 函数，调用 API 获取数据，然后调用 UI 函数更新表格和分页。
绑定查询按钮的点击事件。
绑定表格行的点击事件以触发文件下载（调用 API 获取下载链接或直接请求下载接口）。
实现分页控件的事件处理。
同步管理模块 (filemanage_sync.js):
创建该文件。
实现 loadPendingFiles(page) 函数，调用 API 获取数据，调用 UI 更新表格和分页。
实现 updateSyncStatus() 函数，调用 API 获取状态，调用 UI 更新状态栏和按钮。
绑定 start, pause, resume, stop 按钮的点击事件，调用对应的 API，并在成功后调用 updateSyncStatus()。
实现同步进度更新逻辑（例如，在 updateSyncStatus 中检查状态，如果为 'running'，可能需要额外调用 API 获取已处理 ID，然后调用 UI 函数移除对应行动画）。
实现分页控件的事件处理。


##### 更新导航
请按照当前已有的导航 (resources/templates/fragments/header.html)内容和代码风格
添加指向 /filemanage 的导航链接。


HTML 引入:
在 filemanage.html 的末尾，按依赖顺序引入
common.js (如果需要),
pagination.js (如果需要),
filemanage_api.js,
filemanage_ui.js, 以及后续的功能模块 JS 和
filemanage_main.js。








## 问题

在页面点击“下载文件”后，浏览器出现错误页面：This localhost page can’t be found
No webpage was found for the web address: http://localhost:8080/api/filemanage/encrypted/download?relativePath=&filename=%25E9%2599%2584%25E4%25BB%25B62.%25E7%259C%2581%25E7%25BA****

浏览器F12 CONSOLE 报错信息如下：
chrome-error://chromewebdata/:1    Failed to load resource: the server responded with a status of 404 ()

