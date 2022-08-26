package ua.tn;

import org.postgresql.ds.PGSimpleDataSource;
import ua.tn.entity.Product;
import ua.tn.session.SessionFactory;

import javax.sql.DataSource;

public class Main {
    public static void main(String[] args) {
        var sessionFactory = new SessionFactory(initializeDataSource());
        var firstSession = sessionFactory.createSession();
        var entity1 = firstSession.find(Product.class, 2);
        var entity2 = firstSession.find(Product.class, 2);
        System.out.println(entity1 == entity2);
        firstSession.close();

        var secondSession = sessionFactory.createSession();
        var entity3 = secondSession.find(Product.class, 2);
        System.out.println(entity3 == entity2);
        System.out.println(entity3.equals(entity2));
        secondSession.close();
    }

    private static DataSource initializeDataSource() {
        var dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:5432/postgres");
        dataSource.setUser("postgres");
        return dataSource;
    }
}
