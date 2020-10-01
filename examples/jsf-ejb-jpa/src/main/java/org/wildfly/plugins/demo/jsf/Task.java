package org.wildfly.plugins.demo.jsf;

import static javax.persistence.GenerationType.IDENTITY;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "tasks")
@NamedQuery(name = Task.FIND_ALL, query = "SELECT t FROM Task t")
public class Task {
    public static final String FIND_ALL = "demo.jsf.Task.ALL";

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private String title;

    public Task() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
