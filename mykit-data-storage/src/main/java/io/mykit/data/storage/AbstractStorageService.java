/**
 * Copyright 2020-9999 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mykit.data.storage;

import io.mykit.data.storage.enums.StorageEnum;
import io.mykit.data.storage.exception.StorageException;
import io.mykit.data.storage.query.Query;
import io.mykit.data.storage.strategy.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author binghe
 * @version 1.0.0
 * @description 抽象的存储服务类
 */
public abstract class AbstractStorageService implements StorageService, ApplicationContextAware {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, Strategy> map;

    public abstract List<Map> select(String collectionId, Query query) throws IOException;

    public abstract void insert(String collectionId, Map params) throws IOException;

    public abstract void update(String collectionId, Map params) throws IOException;

    public abstract void delete(String collectionId, String id) throws IOException;

    public abstract void deleteAll(String collectionId) throws IOException;

    /**
     * 记录日志
     */
    public abstract void insertLog(String collectionId, Map<String, Object> params) throws IOException;

    /**
     * 记录错误数据
     */
    public abstract void insertData(String collectionId, List<Map> list) throws IOException;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        map = applicationContext.getBeansOfType(Strategy.class);
    }

    @Override
    public List<Map> query(StorageEnum type, Query query) {
        return query(type, query, null);
    }

    @Override
    public List<Map> query(StorageEnum type, Query query, String collectionId) {
        try {
            collectionId = getCollectionId(type, collectionId);
            return select(collectionId, query);
        } catch (IOException e) {
            logger.error("query collectionId:{}, query:{}, failed:{}", collectionId, query, e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void add(StorageEnum type, Map params) {
        add(type, params, null);
    }

    @Override
    public void add(StorageEnum type, Map params, String collectionId) {
        Assert.notNull(params, "Params can not be null.");
        logger.debug("collectionId:{}, params:{}", collectionId, params);
        try {
            insert(getCollectionId(type, collectionId), params);
        } catch (IOException e) {
            logger.error("add collectionId:{}, params:{}, failed:{}", collectionId, params, e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void edit(StorageEnum type, Map params) {
        edit(type, params, null);
    }

    @Override
    public void edit(StorageEnum type, Map params, String collectionId) {
        Assert.notNull(params, "Params can not be null.");
        logger.debug("collectionId:{}, params:{}", collectionId, params);
        try {
            update(getCollectionId(type, collectionId), params);
        } catch (IOException e) {
            logger.error("edit collectionId:{}, params:{}, failed:{}", collectionId, params, e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void remove(StorageEnum type, String id) {
        remove(type, id, null);
    }

    @Override
    public void remove(StorageEnum type, String id, String collectionId) {
        Assert.hasText(id, "ID can not be null.");
        logger.debug("collectionId:{}, id:{}", collectionId, id);
        try {
            delete(getCollectionId(type, collectionId), id);
        } catch (IOException e) {
            logger.error("remove collectionId:{}, id:{}, failed:{}", collectionId, id, e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void addLog(StorageEnum type, Map<String, Object> params) {
        try {
            insertLog(getCollectionId(type, null), params);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void addData(StorageEnum type, String collectionId, List<Map> list) {
        try {
            insertData(getCollectionId(type, collectionId), list);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new StorageException(e);
        }
    }

    @Override
    public void clear(StorageEnum type, String collectionId) {
        try {
            deleteAll(getCollectionId(type, collectionId));
        } catch (IOException e) {
            logger.error("clear collectionId:{}, failed:{}", collectionId, e.getMessage());
            throw new StorageException(e);
        }
    }

    private String getCollectionId(StorageEnum type, String collectionId) {
        Assert.notNull(type, "StorageEnum can not be null.");
        Strategy strategy = map.get(type.getType().concat("Strategy"));
        Assert.notNull(strategy, "Strategy does not exist.");
        return strategy.createCollectionId(collectionId);
    }
}
