package cgeo.geocaching.log;

import static org.assertj.core.api.Assertions.assertThat;

import cgeo.CGeoTestCase;
import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.models.Image;
import cgeo.geocaching.settings.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LogEntry unit tests
 */
public class LogEntryTest extends CGeoTestCase {

    public static void testLogEntry() {
        final LogEntry logEntry = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();

        assertThat(logEntry.date).isEqualTo(100);
        assertThat(logEntry.getType()).isEqualTo(LogType.FOUND_IT);
        assertThat(logEntry.log).isEqualTo("LOGENTRY");
    }

    public static void testEquals() {
        final LogEntry logEntry1 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY1").build();
        final LogEntry logEntry2 = new LogEntry.Builder().setDate(200).setLogType(LogType.DISCOVERED_IT).setLog("LOGENTRY2").build();

        assertThat(logEntry1).isEqualTo(logEntry1);
        assertThat(logEntry2).isEqualTo(logEntry2);
        assertThat(logEntry1).isNotEqualTo(logEntry2);
    }

    public static void testGetAddLogImage() {
        final Image mockedImage1 = Image.NONE;
        final LogEntry logEntry1 = new LogEntry.Builder()
                .addLogImage(mockedImage1)
                .build();
        assertThat(logEntry1.getLogImages()).hasSize(0);

        final Image mockedImage2 = new Image.Builder().setTitle("").build();
        final LogEntry logEntry2 = new LogEntry.Builder()
                .setDate(100).setLogType(LogType.FOUND_IT)
                .setLog("LOGENTRY")
                .addLogImage(mockedImage2)
                .build();

        assertThat(logEntry2.getLogImages()).hasSize(1);
        assertThat(logEntry2.getLogImages().get(0)).isEqualTo(mockedImage2);
    }

    public static void testGetImageTitles() {
        final String defaultTitle = "• " + CgeoApplication.getInstance().getString(R.string.cache_log_image_default_title);

        LogEntry logEntry = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();

        assertThat(logEntry.getLogImages()).hasSize(0);
        assertThat(logEntry.getImageTitles()).isEqualTo(defaultTitle);

        final Image mockedImage1 = new Image.Builder().setTitle("").build();
        logEntry = logEntry.buildUpon().addLogImage(mockedImage1).build();

        assertThat(logEntry.getLogImages()).hasSize(1);
        assertThat(logEntry.getImageTitles()).isEqualTo(defaultTitle);

        final Image mockedImage2 = new Image.Builder().setTitle("TITLE 1").build();
        logEntry = logEntry.buildUpon().addLogImage(mockedImage2).build();
        final Image mockedImage3 = new Image.Builder().setTitle("TITLE 2").build();
        logEntry = logEntry.buildUpon().addLogImage(mockedImage3).build();

        assertThat(logEntry.getLogImages()).hasSize(3);
        final String titlesWanted = "• TITLE 1\n• TITLE 2";
        assertThat(logEntry.getImageTitles()).isEqualTo(titlesWanted);
    }

    public static void testGetDisplayText() {

        final Boolean oldValue = Settings.getPlainLogs();

        final LogEntry logEntry1 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();
        final LogEntry logEntry2 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("<font color=\"red\">LOGENTRY</font>").build();
        final LogEntry logEntry3 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("<FONT COLOR=\"red\">LOGENTRY</FONT>").build();
        final LogEntry logEntry4 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("<FoNt COlOr=\"red\">LOGENTRY</fOnT>").build();

        Settings.setPlainLogs(false);
        assertThat(logEntry1.getDisplayText()).isEqualTo("LOGENTRY");
        assertThat(logEntry2.getDisplayText()).isEqualTo("<font color=\"red\">LOGENTRY</font>");
        assertThat(logEntry3.getDisplayText()).isEqualTo("<FONT COLOR=\"red\">LOGENTRY</FONT>");
        assertThat(logEntry4.getDisplayText()).isEqualTo("<FoNt COlOr=\"red\">LOGENTRY</fOnT>");

        Settings.setPlainLogs(true);
        assertThat(logEntry1.getDisplayText()).isEqualTo("LOGENTRY");
        assertThat(logEntry2.getDisplayText()).isEqualTo("LOGENTRY");
        assertThat(logEntry3.getDisplayText()).isEqualTo("LOGENTRY");
        assertThat(logEntry4.getDisplayText()).isEqualTo("LOGENTRY");

        Settings.setPlainLogs(oldValue);
    }

    public static void testIsOwn() {
        final LogEntry logEntry1 = new LogEntry.Builder().setAuthor("userthatisnotthedefaultuser").setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();
        final LogEntry logEntry2 = new LogEntry.Builder().setAuthor(Settings.getUserName()).setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();
        final LogEntry logEntry3 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("LOGENTRY").build();

        assertThat(logEntry1.isOwn()).isFalse();
        assertThat(logEntry2.isOwn()).isTrue();
        assertThat(logEntry3.isOwn()).isTrue();
    }

    public static void testComparator() {
        final LogEntry logEntry1 = new LogEntry.Builder().setDate(100).setLogType(LogType.FOUND_IT).setLog("logEntry1 is older than logEntry2").build();
        final LogEntry logEntry2 = new LogEntry.Builder().setDate(200).setLogType(LogType.FOUND_IT).setLog("logEntry2 is more recent than logEntry1").build();

        final List<LogEntry> logList = new ArrayList<>(2);
        logList.add(logEntry1);
        logList.add(logEntry2);

        Collections.sort(logList, LogEntry.DESCENDING_DATE_COMPARATOR);

        assertThat(logList).containsExactly(logEntry2, logEntry1);
    }
}
