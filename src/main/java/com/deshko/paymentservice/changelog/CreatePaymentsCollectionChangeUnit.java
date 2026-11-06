package com.deshko.paymentservice.changelog;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "create-payments-collection", order = "001", author = "kiiitkaaa")
public class CreatePaymentsCollectionChangeUnit {
    private static final String COLLECTION = "payments";

    @Execution
    public void execute(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }
        IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.createIndex(new Index().on("user_id", Sort.Direction.ASC).named("idx_payments_user_id"));
        indexOps.createIndex(new Index().on("order_id", Sort.Direction.ASC).named("idx_payments_order_id"));
        indexOps.createIndex(new Index().on("status", Sort.Direction.ASC).named("idx_payments_status"));
        indexOps.createIndex(new Index()
                .on("user_id", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .named("idx_payments_user_id_timestamp"));
        indexOps.createIndex(new Index().on("timestamp", Sort.Direction.DESC)
                .named("idx_payments_timestamp"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.dropCollection(COLLECTION);
    }
}