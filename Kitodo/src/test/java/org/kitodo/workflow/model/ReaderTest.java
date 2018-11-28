/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.workflow.model;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.camunda.bpm.model.bpmn.instance.Task;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.kitodo.FileLoader;
import org.kitodo.exceptions.WorkflowException;
import org.kitodo.workflow.model.beans.Diagram;
import org.kitodo.workflow.model.beans.TaskInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReaderTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void setUp() throws Exception {
        FileLoader.createExtendedDiagramTestFile();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        FileLoader.deleteExtendedDiagramTestFile();
    }

    @Test
    public void shouldLoadProcess() throws Exception {
        Reader reader = new Reader("extended-test");

        boolean result = Objects.nonNull(reader.getModelInstance());
        assertTrue("Process definition was not loaded!", result);
    }

    @Test
    public void shouldGetWorkflow() throws Exception {
        Reader reader = new Reader("extended-test");

        Diagram workflow = reader.getWorkflow();
        assertEquals("Process definition - workflow was read incorrectly!", "say-hello", workflow.getTitle());
    }

    @Test
    public void shouldReadWorkflow() throws Exception {
        Reader reader = new Reader("extended-test");

        reader.readWorkflowTasks();
        Map<Task, TaskInfo> tasks = reader.getTasks();
        assertEquals("Process definition - workflow was read incorrectly!", 2, tasks.size());

        Set<Map.Entry<Task, TaskInfo>> taskEntries = tasks.entrySet();
        Map.Entry<Task, TaskInfo>[] entry = new Map.Entry[2];
        taskEntries.toArray(entry);

        Task task = entry[0].getKey();
        TaskInfo taskInfo = entry[0].getValue();
        assertCorrectTask(task, taskInfo, "Say hello", 1, "");

        task = entry[1].getKey();
        taskInfo = entry[1].getValue();
        assertCorrectTask(task, taskInfo, "Execute script", 2, "");
    }

    @Test
    public void shouldReadConditionalWorkflow() throws Exception {
        Reader reader = new Reader("gateway-test1");

        reader.readWorkflowTasks();
        Map<Task, TaskInfo> tasks = reader.getTasks();
        assertEquals("Process definition - workflow was read incorrectly!", 5, tasks.size());

        for (Map.Entry<Task, TaskInfo> entry : tasks.entrySet()) {
            Task task = entry.getKey();
            TaskInfo taskInfo = entry.getValue();
            String title = task.getName();
            switch (title) {
                case "Task1":
                    assertCorrectTask(task, taskInfo, "Task1", 1, "");
                    assertFalse("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                case "ScriptTask":
                    assertCorrectTask(task, taskInfo, "ScriptTask", 2, "${type==1}");
                    break;
                case "Task3":
                    assertCorrectTask(task, taskInfo, "Task3", 2, "${type==2}");
                    break;
                case "Task4":
                    assertCorrectTask(task, taskInfo, "Task4", 2, "default");
                    break;
                case "Task5":
                    assertCorrectTask(task, taskInfo, "Task5", 3, "");
                    assertTrue("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                default:
                    fail("Task should have one of the above titles!");
                    break;
            }
        }
    }

    @Test
    public void shouldNotReadConditionalWorkflow() throws Exception {
        Reader reader = new Reader("gateway-test2");

        exception.expect(WorkflowException.class);
        exception.expectMessage(
            "Task in parallel branch can not have second task. Please remove task after task with name 'Task9'.");
        reader.readWorkflowTasks();
    }

    @Test
    public void shouldReadConditionalWorkflowWithTwoEnds() throws Exception {
        Reader reader = new Reader("gateway-test3");

        reader.readWorkflowTasks();
        Map<Task, TaskInfo> tasks = reader.getTasks();
        assertEquals("Process definition - workflow was read incorrectly!", 7, tasks.size());

        for (Map.Entry<Task, TaskInfo> entry : tasks.entrySet()) {
            Task task = entry.getKey();
            TaskInfo taskInfo = entry.getValue();
            String title = task.getName();
            switch (title) {
                case "Task1":
                    assertCorrectTask(task, taskInfo, "Task1", 1, "");
                    assertFalse("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                case "Task2":
                    assertCorrectTask(task, taskInfo, "Task2", 2, "");
                    break;
                case "Task3":
                    assertCorrectTask(task, taskInfo, "Task3", 3, "type=2");
                    assertFalse("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                case "Task4":
                    assertCorrectTask(task, taskInfo, "Task4", 4, "type=2");
                    break;
                case "Task5":
                    assertCorrectTask(task, taskInfo, "Task5", 4, "type=2");
                    break;
                case "Task6":
                    assertCorrectTask(task, taskInfo, "Task6", 5, "type=2");
                    assertTrue("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                case "Task7":
                    assertCorrectTask(task, taskInfo, "Task7", 3, "type=1");
                    assertTrue("Process definition - workflow's task last property were determined incorrectly!",
                            taskInfo.isLast());
                    break;
                default:
                    fail("Task should have one of the above titles!");
                    break;
            }
        }
    }

    private void assertCorrectTask(Task task, TaskInfo taskInfo, String title, int ordering, String condition) {
        assertEquals("Process definition - workflow's task title was read incorrectly!", title, task.getName());
        assertEquals("Process definition - workflow's task ordering was determined incorrectly!", ordering,
            taskInfo.getOrdering());
        assertEquals("Process definition - workflow's task conditions were determined incorrectly!", condition,
            taskInfo.getCondition());
    }
}
