package ua.tn.session;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import ua.tn.annotation.Column;
import ua.tn.annotation.Table;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static lombok.AccessLevel.PACKAGE;

@RequiredArgsConstructor(access = PACKAGE)
public class Session {
    private static final String FIND_SQL_QUERY = "select * from %s where id = ? ";
    private final DataSource dataSource;
    private final Map<EntityKey<?>, Object> entitiesMap = new HashMap<>();

    public <T> T find(Class<T> entityType, Object id) {
        var entityKey = new EntityKey<>(entityType, id);
        var entity = entitiesMap.computeIfAbsent(entityKey, this::loadFromDB);
        return entityType.cast(entity);
    }

    public void close() {
        entitiesMap.clear();
    }

    @SneakyThrows
    private Object loadFromDB(EntityKey<?> entityKey) {
        try (var connection = dataSource.getConnection()) {
            var entityType = entityKey.type();
            var id = entityKey.id();
            var tableName = entityType.getAnnotation(Table.class).value();
            try (var preparedStatement = connection.prepareStatement(String.format(FIND_SQL_QUERY, tableName))) {
                preparedStatement.setObject(1, id);
                var resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    return createEntityFromResultSet(entityType, resultSet);
                } else {
                    throw new NoSuchElementException(String.format("Entity with id = %s was not found", id));
                }
            }
        }
    }

    @SneakyThrows
    private Object createEntityFromResultSet(Class<?> entityType, ResultSet resultSet) {
        var entity = entityType.getConstructor().newInstance();
        for (var field : entityType.getDeclaredFields()) {
            var columnName = resolveColumnName(field);
            var columnValue = resultSet.getObject(columnName);

            if (columnValue instanceof Timestamp timestamp) {
                columnValue = timestamp.toLocalDateTime();
            }

            field.setAccessible(true);
            field.set(entity, columnValue);
        }

        return entity;
    }

    private String resolveColumnName(Field field) {
        return field.isAnnotationPresent(Column.class)
                ? field.getAnnotation(Column.class).value()
                : field.getName();
    }

}
