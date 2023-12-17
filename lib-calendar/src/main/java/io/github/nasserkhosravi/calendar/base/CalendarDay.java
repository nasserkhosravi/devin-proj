package io.github.nasserkhosravi.calendar.base;

import java.util.Objects;
import java.util.TimeZone;


/**
 * A convenience class to represent a specific date.
 */
public class CalendarDay {
    int year;
    int month;
    int day;
    TimeZone mTimeZone;

    public CalendarDay(TimeZone timeZone) {
        mTimeZone = timeZone;
    }

    public CalendarDay(int year, int month, int day) {
        setDay(year, month, day);
    }

    public CalendarDay(int year, int month, int day, TimeZone timezone) {
        mTimeZone = timezone;
        setDay(year, month, day);
    }

    public void set(CalendarDay date) {
        year = date.year;
        month = date.month;
        day = date.day;
    }

    public void setDay(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarDay that = (CalendarDay) o;
        return year == that.year &&
                month == that.month &&
                day == that.day &&
                Objects.equals(mTimeZone, that.mTimeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day, mTimeZone);
    }
}
