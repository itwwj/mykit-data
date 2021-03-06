package io.mykit.data.parser.factory;

import io.mykit.data.cache.service.CacheService;
import io.mykit.data.common.event.FullRefreshEvent;
import io.mykit.data.common.model.Result;
import io.mykit.data.common.model.Task;
import io.mykit.data.common.utils.CollectionUtils;
import io.mykit.data.common.utils.JsonUtils;
import io.mykit.data.connector.config.*;
import io.mykit.data.connector.constants.ConnectorConstants;
import io.mykit.data.connector.enums.ConnectorEnum;
import io.mykit.data.connector.enums.FilterEnum;
import io.mykit.data.connector.enums.OperationEnum;
import io.mykit.data.connector.factory.ConnectorFactory;
import io.mykit.data.monitor.enums.QuartzFilterEnum;
import io.mykit.data.parser.Parser;
import io.mykit.data.parser.ParserException;
import io.mykit.data.parser.enums.ConvertEnum;
import io.mykit.data.parser.enums.ParserEnum;
import io.mykit.data.parser.flush.FlushService;
import io.mykit.data.parser.model.*;
import io.mykit.data.parser.utils.ConvertUtils;
import io.mykit.data.parser.utils.PickerUtils;
import io.mykit.data.plugins.factory.PluginFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class ParserFactory implements Parser {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private PluginFactory pluginFactory;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private FlushService flushService;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public boolean alive(ConnectorConfig config) {
        return connectorFactory.isAlive(config);
    }

    @Override
    public List<String> getTable(ConnectorConfig config) {
        return connectorFactory.getTable(config);
    }

    @Override
    public MetaInfo getMetaInfo(String connectorId, String tableName) {
        ConnectorConfig config = getConnectorConfig(connectorId);
        return connectorFactory.getMetaInfo(config, tableName);
    }

    @Override
    public Map<String, String> getCommand(Mapping mapping, TableGroup tableGroup) {
        final String sourceConnectorId = mapping.getSourceConnectorId();
        final String targetConnectorId = mapping.getTargetConnectorId();
        List<FieldMapping> fieldMapping = tableGroup.getFieldMapping();
        if (CollectionUtils.isEmpty(fieldMapping)) {
            return null;
        }
        String sType = getConnectorConfig(sourceConnectorId).getConnectorType();
        String tType = getConnectorConfig(targetConnectorId).getConnectorType();
        String sTableName = tableGroup.getSourceTable().getName();
        String tTableName = tableGroup.getTargetTable().getName();
        Table sTable = new Table().setName(sTableName).setColumn(new ArrayList<>());
        Table tTable = new Table().setName(tTableName).setColumn(new ArrayList<>());
        fieldMapping.forEach(m -> {
            if (null != m.getSource()) {
                sTable.getColumn().add(m.getSource());
            }
            if (null != m.getTarget()) {
                tTable.getColumn().add(m.getTarget());
            }
        });
        final CommandConfig sourceConfig = new CommandConfig(sType, sTable, tableGroup.getFilter());
        final CommandConfig targetConfig = new CommandConfig(tType, tTable);
        // 获取连接器同步参数
        Map<String, String> command = connectorFactory.getCommand(sourceConfig, targetConfig);
        return command;
    }

    @Override
    public long getCount(String connectorId, Map<String, String> command) {
        ConnectorConfig config = getConnectorConfig(connectorId);
        return connectorFactory.getCount(config, command);
    }

    @Override
    public Connector parseConnector(String json) {
        try {
            JSONObject conn = new JSONObject(json);
            JSONObject config = (JSONObject) conn.remove("config");
            Connector connector = JsonUtils.jsonToObj(conn.toString(), Connector.class);
            Assert.notNull(connector, "Connector can not be null.");
            String connectorType = config.getString("connectorType");
            Class<?> configClass = ConnectorEnum.getConfigClass(connectorType);
            ConnectorConfig obj = (ConnectorConfig) JsonUtils.jsonToObj(config.toString(), configClass);
            connector.setConfig(obj);
            return connector;
        } catch (JSONException e) {
            logger.error(e.getMessage());
            throw new ParserException(e.getMessage());
        }
    }

    @Override
    public <T> T parseObject(String json, Class<T> clazz) {
        try {
            JSONObject obj = new JSONObject(json);
            T t = JsonUtils.jsonToObj(obj.toString(), clazz);
            String format = String.format("%s can not be null.", clazz.getSimpleName());
            Assert.notNull(t, format);
            return t;
        } catch (JSONException e) {
            logger.error(e.getMessage());
            throw new ParserException(e.getMessage());
        }
    }

    @Override
    public List<ConnectorEnum> getConnectorEnumAll() {
        return Arrays.asList(ConnectorEnum.values());
    }

    @Override
    public List<OperationEnum> getOperationEnumAll() {
        return Arrays.asList(OperationEnum.values());
    }

    @Override
    public List<QuartzFilterEnum> getQuartzFilterEnumAll() {
        return Arrays.asList(QuartzFilterEnum.values());
    }

    @Override
    public List<FilterEnum> getFilterEnumAll() {
        return Arrays.asList(FilterEnum.values());
    }

    @Override
    public List<ConvertEnum> getConvertEnumAll() {
        return Arrays.asList(ConvertEnum.values());
    }

    @Override
    public void execute(Task task, Mapping mapping, TableGroup tableGroup) {
        final String metaId = task.getId();
        final String sourceConnectorId = mapping.getSourceConnectorId();
        final String targetConnectorId = mapping.getTargetConnectorId();

        ConnectorConfig sConfig = getConnectorConfig(sourceConnectorId);
        Assert.notNull(sConfig, "数据源配置不能为空.");
        ConnectorConfig tConfig = getConnectorConfig(targetConnectorId);
        Assert.notNull(tConfig, "目标源配置不能为空.");
        TableGroup group = PickerUtils.mergeTableGroupConfig(mapping, tableGroup);
        Map<String, String> command = group.getCommand();
        Assert.notEmpty(command, "执行命令不能为空.");
        List<FieldMapping> fieldMapping = group.getFieldMapping();
        String sTableName = group.getSourceTable().getName();
        String tTableName = group.getTargetTable().getName();
        Assert.notEmpty(fieldMapping, String.format("数据源表[%s]同步到目标源表[%s], 映射关系不能为空.", sTableName, tTableName));
        // 获取同步字段
        Picker picker = new Picker();
        PickerUtils.pickFields(picker, fieldMapping);

        // 检查分页参数
        Map<String, String> params = getMeta(metaId).getMap();
        params.putIfAbsent(ParserEnum.PAGE_INDEX.getCode(), ParserEnum.PAGE_INDEX.getDefaultValue());
        int pageSize = mapping.getReadNum();
        int threadSize = mapping.getThreadNum();
        int batchSize = mapping.getBatchNum();

        for (; ; ) {
            if (!task.isRunning()) {
                logger.warn("任务被中止:{}", metaId);
                break;
            }

            // 1、获取数据源数据
            int pageIndex = Integer.parseInt(params.get(ParserEnum.PAGE_INDEX.getCode()));
            Result reader = connectorFactory.reader(sConfig, command, new ArrayList<>(), pageIndex, pageSize);
            List<Map<String, Object>> data = reader.getData();
            if (CollectionUtils.isEmpty(data)) {
                params.clear();
                logger.info("完成全量同步任务:{}, [{}] >> [{}]", metaId, sTableName, tTableName);
                break;
            }

            // 2、映射字段
            PickerUtils.pickData(picker, data);

            // 3、参数转换
            List<Map<String, Object>> target = picker.getTargetList();
            ConvertUtils.convert(group.getConvert(), target);

            // 4、插件转换
            pluginFactory.convert(group.getPlugin(), data, target);

            // 5、写入目标源
            Result writer = writeBatch(tConfig, command, picker.getTargetFields(), target, threadSize, batchSize);

            // 6、更新结果
            flush(task, writer, target);

            // 7、更新分页数
            params.put(ParserEnum.PAGE_INDEX.getCode(), String.valueOf(++pageIndex));
        }
    }

    @Override
    public void execute(Mapping mapping, TableGroup tableGroup, DataEvent dataEvent) {
        logger.info("{}", dataEvent);
        final String metaId = mapping.getMetaId();

        ConnectorConfig tConfig = getConnectorConfig(mapping.getTargetConnectorId());
        // 获取同步字段
        Picker picker = new Picker();
        PickerUtils.pickFields(picker, tableGroup.getFieldMapping());

        // 1、映射字段
        String event = dataEvent.getEvent();
        Map<String, Object> data = dataEvent.getData();
        PickerUtils.pickData(picker, data);

        // 2、参数转换
        Map<String, Object> target = picker.getTarget();
        ConvertUtils.convert(tableGroup.getConvert(), target);

        // 3、插件转换
        pluginFactory.convert(tableGroup.getPlugin(), event, data, target);

        // 4、写入目标源
        Result writer = connectorFactory.writer(tConfig, picker.getTargetFields(), tableGroup.getCommand(), event, target);

        // 5、更新结果
        List<Map<String, Object>> list = new ArrayList<>(1);
        list.add(target);
        flush(metaId, writer, event, list);
    }

    /**
     * 更新缓存
     *
     * @param task
     * @param writer
     * @param data
     */
    private void flush(Task task, Result writer, List<Map<String, Object>> data) {
        flush(task.getId(), writer, ConnectorConstants.OPERTION_INSERT, data);

        // 发布刷新事件给FullExtractor
        task.setEndTime(Instant.now().toEpochMilli());
        applicationContext.publishEvent(new FullRefreshEvent(applicationContext, task));
    }

    private void flush(String metaId, Result writer, String event, List<Map<String, Object>> data) {
        // 引用传递
        long total = data.size();
        long fail = writer.getFail().get();
        Meta meta = getMeta(metaId);
        meta.getFail().getAndAdd(fail);
        meta.getSuccess().getAndAdd(total - fail);

        // 记录错误数据
        Queue<Map<String, Object>> failData = writer.getFailData();
        boolean success = CollectionUtils.isEmpty(failData);
        if (!success) {
            data.clear();
            data.addAll(failData);
        }
        String error = writer.getError().toString();
        flushService.asyncWrite(metaId, event, success, data, error);
    }

    /**
     * 获取Meta(注: 没有bean拷贝, 便于直接更新缓存)
     *
     * @param metaId
     * @return
     */
    private Meta getMeta(String metaId) {
        Assert.hasText(metaId, "Meta id can not be empty.");
        Meta meta = cacheService.get(metaId, Meta.class);
        Assert.notNull(meta, "Meta can not be null.");
        return meta;
    }

    /**
     * 获取连接配置
     *
     * @param connectorId
     * @return
     */
    private ConnectorConfig getConnectorConfig(String connectorId) {
        Assert.hasText(connectorId, "Connector id can not be empty.");
        Connector conn = cacheService.get(connectorId, Connector.class);
        Assert.notNull(conn, "Connector can not be null.");
        Connector connector = new Connector();
        BeanUtils.copyProperties(conn, connector);
        return connector.getConfig();
    }

    /**
     * 批量写入
     *
     * @param config
     * @param command
     * @param fields
     * @param target
     * @param threadSize
     * @param batchSize
     * @return
     */
    private Result writeBatch(ConnectorConfig config, Map<String, String> command, List<Field> fields, List<Map<String, Object>> target,
                              int threadSize, int batchSize) {
        // 总数
        int total = target.size();
        // 单次任务
        if (total <= batchSize) {
            return connectorFactory.writer(config, command, fields, target);
        }

        // 批量任务, 拆分
        int taskSize = total % batchSize == 0 ? total / batchSize : total / batchSize + 1;
        threadSize = taskSize <= threadSize ? taskSize : threadSize;

        // 转换为消息队列，根据batchSize获取数据，并发写入
        Queue<Map<String, Object>> queue = new ConcurrentLinkedQueue<>(target);

        // 创建线程池
        final ThreadPoolTaskExecutor executor = getThreadPoolTaskExecutor(threadSize, taskSize - threadSize);
        final Result result = new Result();
        for (; ; ) {
            if (taskSize <= 0) {
                break;
            }
            // TODO 优化 CountDownLatch
            final CountDownLatch latch = new CountDownLatch(threadSize);
            for (int i = 0; i < threadSize; i++) {
                executor.execute(() -> {
                    try {
                        Result w = parallelTask(batchSize, queue, config, command, fields);
                        // CAS
                        result.getFailData().addAll(w.getFailData());
                        result.getFail().getAndAdd(w.getFail().get());
                        result.getError().append(w.getError());
                    } catch (Exception e) {
                        result.getError().append(e.getMessage()).append("\r\n");
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }

            taskSize -= threadSize;
        }

        executor.shutdown();
        return result;
    }

    private Result parallelTask(int batchSize, Queue<Map<String, Object>> queue, ConnectorConfig config, Map<String, String> command,
                                List<Field> fields) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int j = 0; j < batchSize; j++) {
            Map<String, Object> poll = queue.poll();
            if (null == poll) {
                break;
            }
            data.add(poll);
        }
        return connectorFactory.writer(config, command, fields, data);
    }

    private ThreadPoolTaskExecutor getThreadPoolTaskExecutor(int threadSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadSize);
        executor.setMaxPoolSize(threadSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(30);
        executor.setAwaitTerminationSeconds(30);
        executor.setThreadNamePrefix("ParserExecutor");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

}