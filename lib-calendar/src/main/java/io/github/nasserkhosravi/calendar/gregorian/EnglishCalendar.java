
/**
 * Persian Calendar see: http://code.google.com/p/persian-calendar/
 * Copyright (C) 2012  Mortezaadi@gmail.com
 * PersianCalendar.java
 * Persian Calendar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package io.github.nasserkhosravi.calendar.gregorian;


import io.github.nasserkhosravi.calendar.CalendarExtKt;
import io.github.nasserkhosravi.calendar.base.BaseCalendar;
import io.github.nasserkhosravi.calendar.base.MonthProvider;
import io.github.nasserkhosravi.calendar.base.WeekDayProvider;
import io.github.nasserkhosravi.calendar.base.YearMonthDay;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Updated By ArashJahani on 2019/09/30
 */

public class EnglishCalendar extends GregorianCalendar implements BaseCalendar {

    private static final MonthProvider monthProvider = new RegularMonthProvider();

    private static final WeekDayProvider weekProvider = new RegularWeekProvider();
    private static EnglishCalendar calendar;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    private int mYear;
    /*
     * month number in gregorian Calendar , it starts from 0
     */
    private int mMonth;
    private int mDay;
    // use to separate PersianDate's field and also Parse the DateString based
    // on this delimiter
    private final String delimiter = "/";

    /**
     * default constructor
     * <p>
     * most of the time we don't care about TimeZone when we persisting Date or
     * doing some calculation on date. <strong> Default TimeZone was set to
     * "GMT" </strong> in order to make developer to work more convenient with
     * the library; however you can change the TimeZone as you do in
     * GregorianCalendar by calling setTimeZone()
     */
    public EnglishCalendar() {
        super(TimeZone.getDefault(), getLocale());
    }

    @NotNull
    private static Locale getLocale() {
        return new Locale("ps");
    }

    @NotNull
    public static EnglishCalendar getInstance() {
        if (calendar == null) {
            calendar = new EnglishCalendar();
        }
        return calendar;
    }

    /**
     * Calculate date from current Date and populates the corresponding
     * fields(persianYear, persianMonth, persianDay)
     */
    public void calculateDate() {
        Calendar calendar = Calendar.getInstance();
        this.mYear = calendar.get(Calendar.YEAR);
        this.mMonth = calendar.get(Calendar.MONTH);
        this.mDay = calendar.get(Calendar.DAY_OF_MONTH);
    }

    public void setDate(int year, int month, int day) {
        set(year, month, day);
        this.mYear = get(Calendar.YEAR);
        this.mMonth = get(Calendar.MONTH);
        this.mDay = get(Calendar.DAY_OF_MONTH);

    }


    public int getYear() {
        return this.mYear;
    }

    /**
     * @return int persian month number
     */
    public int getMonth() {
        return this.mMonth;
    }

    /**
     * @return String persian month name
     */
    @NotNull
    public String getMonthName() {
        return getMonthNameInfo().getMonthNames()[this.mMonth];
    }

    /**
     * @return int Persian day in month
     */
    public int getDay() {
        return this.mDay;
    }

    @Override
    public int getDayOfWeek() {
        return get(DAY_OF_WEEK);
    }

    /**
     * @return String Name of the day in week
     */
    @NotNull
    public String getWeekDayName() {
        return weekProvider.getDayNameBy(getDayOfWeek());
    }

    /**
     * @return String Name of the day in week
     */
    @NotNull
    public String getWeekDayNameShortType() {
        return weekProvider.getDayShortNameBy(getDayOfWeek());
    }

    @Override
    public int getFirstDayOfWeek() {
        return Calendar.getInstance().getFirstDayOfWeek();
    }

    @Override
    public BaseCalendar parseDate(@NotNull String date) {
        try {
            Date mDate = dateFormat.parse(date);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(mDate);
            BaseCalendar baseCalendar = createNewInstance();
            baseCalendar.setDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            return baseCalendar;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @return String of Persian Date ex: شنبه 01 خرداد 1361
     */
    @NotNull
    public String getLongDate() {
        return getWeekDayName() + " "
                + this.mDay + " "
                + getMonthName() + " " + this.mYear;

    }

    @NotNull
    @Override
    public String getShortDateFormat() {
        return getWeekDayName() + " "
                + this.mDay + " "
                + getMonthName();
    }

    /**
     * @return String of Persian Date ex: شنبه 01 خرداد 1361
     */
    public String getShortDatePersianFormat() {
        return getWeekDayName() + " "
                + this.mDay + " "
                + getMonthName();

    }

    /**
     * @return String of persian date formatted by
     * 'YYYY[delimiter]mm[delimiter]dd' default delimiter is '/'
     */
    @NotNull
    public String getShortDate() {
        return "" + formatToMilitary(this.mYear) + delimiter
                + formatToMilitary(getMonth()) + delimiter
                + formatToMilitary(this.mDay);
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance(int year, int month, int day) {
        BaseCalendar calendar = createNewInstance();
        calendar.setDate(year, month, day);
        return calendar;
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance() {
        return new EnglishCalendar();
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance(long time) {
        BaseCalendar calendar = createNewInstance();
        calendar.setTimeInMillis(time);
        return calendar;
    }

    @NotNull
    @Override
    public BaseCalendar minusYear(int amount) {
        return CalendarExtKt.minusYear(this, amount);
    }

    @NotNull
    @Override
    public BaseCalendar plusYear(int amount) {
        return CalendarExtKt.plusYear(this, amount);
    }

    @NotNull
    @Override
    public BaseCalendar minusDay(int amount) {
        return CalendarExtKt.minusDay(this, amount);
    }

    @NotNull
    @Override
    public BaseCalendar plusDay(int amount) {
        return CalendarExtKt.plusDay(this, amount);
    }

    @NotNull
    @Override
    public BaseCalendar getStartOfYearDate() {
        BaseCalendar calendar = createNewInstance();
        calendar.setDate(getYear(), 0, 1);
        return calendar;
    }

    @NotNull
    @Override
    public BaseCalendar getEndOfYearDate() {
        BaseCalendar calendar = createNewInstance();
        calendar.setDate(getYear(), 11, getMonthDaysCountOf(12, getYear()));
        return calendar;
    }

    @Override
    public boolean isWeekend(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day);
        int dayOfWeek = calendar.get(DAY_OF_WEEK);
        return (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY);
    }

    @Override
    public int getMonthDaysCountOf(int month, int year) {
        GregorianCalendar myCal = new GregorianCalendar(year, month, 1);
        return myCal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private String formatToMilitary(int i) {
        return (i < 9) ? "0" + i : String.valueOf(i);
    }

    @NotNull
    @Override
    public String toString() {
        String str = super.toString();
        return str.substring(0, str.length() - 1) + ",PersianDate="
                + getShortDate() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BaseCalendar))
            return false;
        return CalendarExtKt.isEqualYearMonthDay(this, (BaseCalendar) obj);
    }

    @Override
    public boolean isAfterDate(YearMonthDay date) {
        BaseCalendar mDate = createNewInstance();
        mDate.setDate(date.getYear(), date.getMonth(), date.getDay());
        return this.after(mDate);
    }

    public boolean isBeforeDate(YearMonthDay date) {
        BaseCalendar mDate = createNewInstance();
        mDate.setDate(date.getYear(), date.getMonth(), date.getDay());
        return this.before(mDate);
    }

    @Override
    public boolean isAfterDate(BaseCalendar date) {
        BaseCalendar mDate = createNewInstance();
        mDate.setDate(date.getYear(), date.getMonth(), date.getDay());
        return this.after(mDate);
    }

    @Override
    public boolean isBeforeDate(BaseCalendar date) {
        BaseCalendar mDate = createNewInstance();
        mDate.setDate(date.getYear(), date.getMonth(), date.getDay());
        return this.before(mDate);
    }

    public Date getDateWithZeroTime() {

        Calendar calendar = Calendar.getInstance();

        calendar.setTime(this.getTime());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();

    }

    @NotNull
    @Override
    public BaseCalendar clone() {
        EnglishCalendar calendar = new EnglishCalendar();
        calendar.set(mYear, mMonth, mDay);
        return calendar;
    }

    @Override
    public int hashCode() {
        int result = 31;
        result *= (mYear * 17);
        result *= (mMonth + 11);
        result *= (mDay + 13);
        return result;
    }

    @Override
    public void set(int field, int value) {
        super.set(field, value);
        updateLocalFields();
    }

    @Override
    public void setTimeInMillis(long millis) {
        super.setTimeInMillis(millis);
        updateLocalFields();
    }

    private void updateLocalFields() {
        this.mYear = get(Calendar.YEAR);
        //todo: is zero index get(Calendar.MONTH)
        this.mMonth = get(Calendar.MONTH);
        this.mDay = get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public void setTimeZone(TimeZone zone) {
        super.setTimeZone(zone);
        calculateDate();
    }

    @NotNull
    @Override
    public WeekDayProvider getWeekNameInfo() {
        return weekProvider;
    }

    @NotNull
    @Override
    public MonthProvider getMonthNameInfo() {
        return monthProvider;
    }

    @NotNull
    @Override
    public String getCalendarName() {
        return "english";
    }
}

