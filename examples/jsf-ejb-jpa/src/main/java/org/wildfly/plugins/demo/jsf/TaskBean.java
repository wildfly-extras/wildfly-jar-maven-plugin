package org.wildfly.plugins.demo.jsf;

import java.io.Serializable;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

@Named
@ViewScoped
public class TaskBean implements Serializable {

    private List<Task> allTasks;

    @Inject
    private TaskController controller;

    @PostConstruct
    private void postConstruct() {
        refresh();
    }

    public void delete(String id) {
        try {
            controller.delete(Long.valueOf(id));
            refresh();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Task " + id + " deleted"));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Error deleting the Task by Id. " + e.getLocalizedMessage()));
        }
    }

    public void add(String title) {
        try {
            controller.add(title);
            refresh();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Task with title " + title + " created"));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Error adding a new Task. " + e.getLocalizedMessage()));
        }
    }

    public void update(String id, String title) {
        try {
            controller.update(Long.valueOf(id), title);
            refresh();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Task " + id + " updated"));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Error updating the Task by Id. " + e.getLocalizedMessage()));
        }
    }

    private void refresh() {
        this.allTasks = controller.loadAll();
    }

    public List<Task> getAllTasks() {
        return allTasks;
    }
}
