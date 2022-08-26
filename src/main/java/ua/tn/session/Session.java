package ua.tn.session;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ua.tn.annotation.Column;
import ua.tn.annotation.Table;
import ua.tn.exception.SessionOperationException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static lombok.AccessLevel.PACKAGE;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)

public class Session {
    private static final String FIND_SQL_QUERY = "select * from %s where id = ? ";
    private static final String UPDATE_SQL_QUERY = "update %s set %s where id = ? ";
    private final DataSource dataSource;
    private final Map<EntityKey<?>, Object> entitiesMap = new HashMap<>();
    private final Map<EntityKey<?>, Object[]> snapshotsMap = new HashMap<>();

    public <T> T find(Class<T> entityType, Object id) {
        var entityKey = new EntityKey<>(entityType, id);
        var entity = entitiesMap.computeIfAbsent(entityKey, this::loadFromDB);
        return entityType.cast(entity);
    }

    public void close() {
        entitiesMap.entrySet()
                .stream()
                .filter(this::isEntityChanged)
                .forEach(this::executeEntityUpdate);
        entitiesMap.clear();
        snapshotsMap.clear();
    }

    @SneakyThrows
    private Object loadFromDB(EntityKey<?> entityKey) {
        try (var connection = dataSource.getConnection()) {
            var entityType = entityKey.type();
            var id = entityKey.id();
            var tableName = entityType.getAnnotation(Table.class).value();
            try (var preparedStatement = connection.prepareStatement(String.format(FIND_SQL_QUERY, tableName))) {
                preparedStatement.setObject(1, id);
                log.info("SQL: {}", preparedStatement);
                var resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    return createEntityFromResultSet(entityKey, resultSet);
                } else {
                    throw new NoSuchElementException(String.format("Entity with id = %s was not found", id));
                }
            }
        }
    }

    @SneakyThrows
    private Object createEntityFromResultSet(EntityKey<?> entityKey, ResultSet resultSet) {
        var entityType = entityKey.type();
        var entity = entityType.getConstructor().newInstance();
        var sortedDeclaredFields = Arrays.stream(entityType.getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName)).toArray(Field[]::new);

        var snapshots = new Object[sortedDeclaredFields.length];

        for (int i = 0; i < sortedDeclaredFields.length; i++) {
            var field = sortedDeclaredFields[i];
            var columnName = resolveColumnName(field);
            var columnValue = resultSet.getObject(columnName);

            if (columnValue instanceof Timestamp timestamp) {
                columnValue = timestamp.toLocalDateTime();
            }

            field.setAccessible(true);
            field.set(entity, columnValue);
            snapshots[i] = columnValue;
        }

        snapshotsMap.put(entityKey, snapshots);
        return entity;
    }

    @SneakyThrows
    private boolean isEntityChanged(Map.Entry<EntityKey<?>, Object> entityEntry) {
        var entity = entityEntry.getValue();
        var entityType = entity.getClass();
        var currentFields = Arrays.stream(entityType.getDeclaredFields())
                .sorted(Comparator.comparing(Field::getName)).toArray(Field[]::new);
        var snapshotFields = snapshotsMap.get(entityEntry.getKey());
        for (int i = 0; i < currentFields.length; i++) {
            var currentField = currentFields[i];
            currentField.setAccessible(true);
            if (!currentField.get(entity).equals(snapshotFields[i])) {
                return true;
            }
        }
        return false;
    }

    @SneakyThrows
    private void executeEntityUpdate(Map.Entry<EntityKey<?>, Object> entityEntry) {
        var entityKey = entityEntry.getKey();
        var id = entityKey.id();
        try (var connection = dataSource.getConnection()) {
            try (var preparedStatement = connection.prepareStatement(prepareUpdateSqlQuery(entityKey))) {
                preparedStatement.setObject(1, id);
                log.info("SQL: {}", preparedStatement);
                int updatedRows = preparedStatement.executeUpdate();

                if (updatedRows == 0) {
                    throw new SessionOperationException(String.format("Error updating entity with id = %s", id));
                }
            }
        }
    }

    @SneakyThrows
    private String prepareUpdateSqlQuery(EntityKey<?> entityKey) {
        var entityType = entityKey.type();
        var tableName = entityType.getAnnotation(Table.class).value();
        var updatedEntity = entitiesMap.get(entityKey);
        var sqlSetPart = new StringBuilder();

        for (var field : entityType.getDeclaredFields()) {
            field.setAccessible(true);
            sqlSetPart.append(resolveColumnName(field));
            sqlSetPart.append(" = ");
            var fieldValue = field.get(updatedEntity);
            sqlSetPart.append("'");
            sqlSetPart.append(fieldValue.toString());
            sqlSetPart.append("'");
            sqlSetPart.append(", ");
        }

        sqlSetPart.deleteCharAt(sqlSetPart.lastIndexOf(","));
        return String.format(UPDATE_SQL_QUERY, tableName, sqlSetPart);
    }

    private String resolveColumnName(Field field) {
        return field.isAnnotationPresent(Column.class)
                ? field.getAnnotation(Column.class).value()
                : field.getName();
    }

}
