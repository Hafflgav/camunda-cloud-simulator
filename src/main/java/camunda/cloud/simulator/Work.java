package camunda.cloud.simulator;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


abstract class Work<T> {
    /**
     * Todo: Decide how to deal with Process Instances
     * Afaik we can only get the active jobs from Zeebe and influence them?
     * Or do we need to use Operate's API?
     */
    protected T workItem;
    protected String procsessInstanceID;
    private Map<Object, Date> dueCache = new HashMap<>();
    abstract protected Date calculateNewRandomDue();

    public Work(T workItem, String piID) {
        this.workItem = workItem;
        this.procsessInstanceID = piID;
    }

    public void execute() {
        /**
         * Todo: Execute Task
         */

        // If we are a recurring job, we have to make sure that due date is newly
        // calculated after each execution. To do so, we simply remove it from
        // cache in every case.
        dueCache.remove(workItem);
    };


    public Date getDue() {
        if (!dueCache.containsKey(workItem)) {
            dueCache.put(workItem, calculateNewRandomDue());
        }
        return dueCache.get(workItem);
    }
}