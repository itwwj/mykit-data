package io.mykit.data.parser.logger;

/**
 * 日志类型枚举
 */
public interface LogType {

    /**
     * 分类名称
     *
     * @return
     */
    String getName();

    /**
     * 类型
     *
     * @return
     */
    String getType();

    /**
     * 内容
     *
     * @return
     */
    String getMessage();

    /**
     * 系统日志1
     */
    enum SystemLog implements LogType {
        /**
         * 正常
         */
        INFO("10", "正常"),
        /**
         * 警告
         */
        WARN("11", "警告"),
        /**
         * 错误
         */
        ERROR("12", "错误");

        private String type;
        private String message;

        SystemLog(String type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public String getName() {
            return "系统日志";
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * 连接器2
     */
    enum ConnectorLog implements LogType {
        INSERT("20", "新增"),
        UPDATE("21", "修改"),
        DELETE("22", "删除"),
        FAILED("23", "连接失败");

        private String type;
        private String message;

        ConnectorLog(String type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public String getName() {
            return "连接器";
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * 驱动3
     */
    enum MappingLog implements LogType {
        INSERT("30", "新增"),
        UPDATE("31", "修改"),
        DELETE("32", "删除"),
        RUNNING("33", "启动"),
        STOP("34", "停止");

        private String type;
        private String message;

        MappingLog(String type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public String getName() {
            return "驱动";
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * 映射关系4
     */
    enum TableGroupLog implements LogType {
        INSERT("40", "新增"),
        UPDATE("41", "修改"),
        DELETE("42", "删除"),
        INCREMENT_FAILED("43", "增量同步异常");

        private String type;
        private String message;

        TableGroupLog(String type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public String getName() {
            return "映射关系";
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    /**
     * 元信息5
     */
    enum MetaLog implements LogType {
        DELETE("50", "删除"),
        CLEAR("51", "删除数据"),
        TASK("52", "任务");

        private String type;
        private String message;

        MetaLog(String type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public String getName() {
            return "元信息";
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

}