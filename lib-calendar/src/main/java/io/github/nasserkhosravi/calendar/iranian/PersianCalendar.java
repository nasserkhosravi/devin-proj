
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


package io.github.nasserkhosravi.calendar.iranian;


import io.github.nasserkhosravi.calendar.CalendarExtKt;
import io.github.nasserkhosravi.calendar.IntRange;
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

public class PersianCalendar extends GregorianCalendar implements BaseCalendar {

    private static final MonthProvider monthProvider = new AvestanMonthProvider();

    /**
     * The JDN of 1 Farvardin 1; Equivalent to March 19, 622 A.D.
     */
    public static final long PERSIAN_EPOCH = 1948321;
    private static final WeekDayProvider weekProvider = new IranianWeekProvider();
    private static PersianCalendar calendar;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    private int persianYear;
    /*
     * month number in persian calendar, it starts from 0
     */
    private int persianMonth;
    private int persianDay;
    // use to separate PersianDate's field and also Parse the DateString based
    // on this delimiter
    private String delimiter = "/";

    // Helper Functions
    private static final int[] gregorianDaysInMonth = {31, 28, 31, 30, 31, 30, 31,
            31, 30, 31, 30, 31};
    private static final int[] persianDaysInMonth = {31, 31, 31, 31, 31, 31, 30, 30,
            30, 30, 30, 29};

    /**
     * default constructor
     * <p>
     * most of the time we don't care about TimeZone when we persisting Date or
     * doing some calculation on date. <strong> Default TimeZone was set to
     * "GMT" </strong> in order to make developer to work more convenient with
     * the library; however you can change the TimeZone as you do in
     * GregorianCalendar by calling setTimeZone()
     */
    public PersianCalendar() {
        super(TimeZone.getDefault(), Locale.getDefault());
    }

    @NotNull
    public static PersianCalendar getInstance() {
        if (calendar == null) {
            calendar = new PersianCalendar();
        }
        return calendar;
    }

    /**
     * Calculate persian date from current Date and populates the corresponding
     * fields(persianYear, persianMonth, persianDay)
     */
    public void calculateDate() {
        YearMonthDay persianYearMonthDay = PersianCalendar.gregorianToJalali(
                new YearMonthDay(this.get(PersianCalendar.YEAR), this.get(PersianCalendar.MONTH), this.get(PersianCalendar.DAY_OF_MONTH))
        );

        this.persianYear = persianYearMonthDay.getYear();
        this.persianMonth = persianYearMonthDay.getMonth();
        this.persianDay = persianYearMonthDay.getDay();
    }

    /**
     * Determines if the given year is a leap year in persian calendar. Returns
     * true if the given year is a leap year.
     *
     * @return boolean
     */
    public boolean isPersianLeapYear() {
        return CalendarUtils.isPersianLeapYear(this.persianYear);
    }

    /**
     * set the persian date it converts PersianDate to the Julian and assigned
     * equivalent milliseconds to the instance
     *
     * @param persianYear
     * @param persianMonth
     * @param persianDay
     */
    @Override
    public void setDate(int persianYear, @IntRange(from = 0, to = 11) int persianMonth, int persianDay) {

        persianMonth += 1;
        this.persianYear = persianYear;
        this.persianMonth = persianMonth;
        this.persianDay = persianDay;

        YearMonthDay gregorianYearMonthDay = persianToGregorian(new YearMonthDay(persianYear, this.persianMonth - 1, persianDay));
        this.set(gregorianYearMonthDay.getYear(), gregorianYearMonthDay.getMonth(), gregorianYearMonthDay.getDay());
        calculateDate();
    }

    public int getYear() {
        return this.persianYear;
    }

    /**
     * @return int persian month number,
     */
    @Override
    public int getMonth() {
        //todo: fix situation of month and month index and write a good comment for them
        return getMonthIndex();
    }

    public int getMonthIndex() {
        return this.persianMonth;
    }

    /**
     * @return int Persian day in month
     */
    public int getDay() {
        return this.persianDay;
    }

    public int getHour() {
        return get(HOUR_OF_DAY);
    }

    public int getMinute() {
        return get(MINUTE);
    }

    public int getSecond() {
        return get(SECOND);
    }

    private static YearMonthDay persianToGregorian(YearMonthDay persian) {
        int month = persian.getMonth();
        if (month > 11 || month < -11) {
            throw new IllegalArgumentException("wrong month: " + month);
        }

        int gregorianYear;
        int gregorianMonth;
        int gregorianDay;

        int gregorianDayNo, persianDayNo;
        int leap;

        int i;
        persian.setYear(persian.getYear() - 979);
        persian.setDay(persian.getDay() - 1);

        persianDayNo = 365 * persian.getYear() + (int) (persian.getYear() / 33) * 8
                + (int) Math.floor(((persian.getYear() % 33) + 3) / 4);
        for (i = 0; i < month; ++i) {
            persianDayNo += persianDaysInMonth[i];
        }

        persianDayNo += persian.getDay();

        gregorianDayNo = persianDayNo + 79;

        gregorianYear = 1600 + 400 * (int) Math.floor(gregorianDayNo / 146097); /* 146097 = 365*400 + 400/4 - 400/100 + 400/400 */
        gregorianDayNo = gregorianDayNo % 146097;

        leap = 1;
        if (gregorianDayNo >= 36525) /* 36525 = 365*100 + 100/4 */ {
            gregorianDayNo--;
            gregorianYear += 100 * (int) Math.floor(gregorianDayNo / 36524); /* 36524 = 365*100 + 100/4 - 100/100 */
            gregorianDayNo = gregorianDayNo % 36524;

            if (gregorianDayNo >= 365) {
                gregorianDayNo++;
            } else {
                leap = 0;
            }
        }

        gregorianYear += 4 * (int) Math.floor(gregorianDayNo / 1461); /* 1461 = 365*4 + 4/4 */
        gregorianDayNo = gregorianDayNo % 1461;

        if (gregorianDayNo >= 366) {
            leap = 0;

            gregorianDayNo--;
            gregorianYear += (int) Math.floor(gregorianDayNo / 365);
            gregorianDayNo = gregorianDayNo % 365;
        }

        for (i = 0; gregorianDayNo >= gregorianDaysInMonth[i] + ((i == 1 && leap == 1) ? i : 0); i++) {
            gregorianDayNo -= gregorianDaysInMonth[i] + ((i == 1 && leap == 1) ? i : 0);
        }
        gregorianMonth = i;
        gregorianDay = gregorianDayNo + 1;

        return new YearMonthDay(gregorianYear, gregorianMonth, gregorianDay);

    }

    @Override
    public int getDayOfWeek() {
        return get(DAY_OF_WEEK);
    }

    public String getPersianLongDateAndTime() {
        return getLongDate() + " ساعت " + get(HOUR_OF_DAY) + ":" + get(MINUTE) + ":" + get(SECOND);
    }

    /**
     * @return String persian month name
     */
    @NotNull
    public String getMonthName() {
        return getMonthNameInfo().getMonthName(getMonthIndex());
    }

    /**
     * @return String Name of the day in week
     */
    @NotNull
    public String getWeekDayName() {
        return getWeekNameInfo().getDayNameBy(getDayOfWeek());
    }

    @Override
    public boolean isWeekend(int year, @IntRange(from = 0, to = 11) int month, int day) {
        BaseCalendar calendar = createNewInstance(year, month, day);
        int dayOfWeek = calendar.get(DAY_OF_WEEK);
        return (dayOfWeek == Calendar.FRIDAY);
    }

    @Override
    public int getFirstDayOfWeek() {
        return Calendar.SATURDAY;
    }

    @Override
    public int getMonthDaysCountOf(@IntRange(from = 0, to = 11) int month, int year) {
        if (month < 6) {
            return 31;
        } else if (month < 11) {
            return 30;
        } else {
            if (CalendarUtils.isPersianLeapYear(year)) return 30;
            else return 29;
        }
    }

    @Override
    public BaseCalendar parseDate(@NotNull String date) {
        try {
            Date mDate = dateFormat.parse(date);
            if (mDate == null) {
                throw new NullPointerException("wrong format date");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(mDate);
            YearMonthDay pDate = gregorianToJalali(new YearMonthDay(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)));
            return createNewInstance(pDate.getYear(), pDate.getMonth(), pDate.getDay());
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
                + this.persianDay + " "
                + getMonthName() + " " + this.persianYear;

    }

    @NotNull
    @Override
    public String getShortDateFormat() {
        return getWeekDayName() + " "
                + this.persianDay + " "
                + getMonthName();
    }

    /**
     * @return String of Persian Date ex: شنبه 01 خرداد 1361
     */
    public String getShortDatePersianFormat() {
        return getWeekDayName() + " "
                + this.persianDay + " "
                + getMonthName();

    }

    /**
     * @return String of persian date formatted by
     * 'YYYY[delimiter]mm[delimiter]dd' default delimiter is '/'
     */
    @NotNull
    public String getShortDate() {
        return "" + formatToMilitary(this.persianYear) + delimiter
                + formatToMilitary(getMonth()) + delimiter
                + formatToMilitary(this.persianDay);
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance(int year, @IntRange(from = 0, to = 11) int month, int day) {
        BaseCalendar calendar = createNewInstance();
        calendar.setDate(year, month, day);
        return calendar;
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance() {
        return new PersianCalendar();
    }

    @NotNull
    @Override
    public BaseCalendar createNewInstance(long time) {
        BaseCalendar calendar = createNewInstance();
        calendar.setTimeInMillis(time);
        return calendar;
    }

    private BaseCalendar createNewInstance(YearMonthDay date) {
        return createNewInstance(date.getYear(), date.getMonth(), date.getDay());
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
        return createNewInstance(getYear(), 0, 1);
    }

    @NotNull
    @Override
    public BaseCalendar getEndOfYearDate() {
        return createNewInstance(getYear(), 11, getMonthDaysCountOf(12, getYear()));
    }

    private String formatToMilitary(int i) {
        return (i < 9) ? "0" + i : String.valueOf(i);
    }

    private static YearMonthDay gregorianToJalali(YearMonthDay gregorian) {
        int month = gregorian.getMonth();
        if (month > 11 || month < -11) {
            throw new IllegalArgumentException("Wrong month: " + month);
        }
        int persianYear;
        int persianMonth;
        int persianDay;

        int gregorianDayNo, persianDayNo;
        int persianNP;
        int i;

        gregorian.setYear(gregorian.getYear() - 1600);
        gregorian.setDay(gregorian.getDay() - 1);

        gregorianDayNo = 365 * gregorian.getYear() + (int) Math.floor((gregorian.getYear() + 3) / 4)
                - (int) Math.floor((gregorian.getYear() + 99) / 100)
                + (int) Math.floor((gregorian.getYear() + 399) / 400);
        for (i = 0; i < month; ++i) {
            gregorianDayNo += gregorianDaysInMonth[i];
        }

        if (month > 1 && ((gregorian.getYear() % 4 == 0 && gregorian.getYear() % 100 != 0)
                || (gregorian.getYear() % 400 == 0))) {
            ++gregorianDayNo;
        }

        gregorianDayNo += gregorian.getDay();

        persianDayNo = gregorianDayNo - 79;

        persianNP = (int) Math.floor(persianDayNo / 12053);
        persianDayNo = persianDayNo % 12053;

        persianYear = 979 + 33 * persianNP + 4 * (int) (persianDayNo / 1461);
        persianDayNo = persianDayNo % 1461;

        if (persianDayNo >= 366) {
            persianYear += (int) Math.floor((persianDayNo - 1) / 365);
            persianDayNo = (persianDayNo - 1) % 365;
        }

        for (i = 0; i < 11 && persianDayNo >= persianDaysInMonth[i]; ++i) {
            persianDayNo -= persianDaysInMonth[i];
        }
        persianMonth = i;
        persianDay = persianDayNo + 1;

        return new YearMonthDay(persianYear, persianMonth, persianDay);
    }

    /**
     * <pre>
     *    use <code>{@link PersianDateParser}</code> to parse string
     *    and get the Persian Date.
     * </pre>
     *
     * @param dateString
     * @see PersianDateParser
     */
    public void parse(String dateString) {
        PersianCalendar p = new PersianDateParser(dateString, delimiter)
                .getPersianDate();
        setDate(p.getYear(), p.getMonth(), p.getDay());
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * assign delimiter to use as a separator of date fields.
     *
     * @param delimiter
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
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

    public boolean isBeforeDate(YearMonthDay date) {
        return isBeforeDate(createNewInstance(date));
    }

    @Override
    public boolean isAfterDate(@NotNull YearMonthDay date) {
        return isAfterDate(createNewInstance(date));
    }

    @Override
    public boolean isAfterDate(@NotNull BaseCalendar date) {
        return CalendarExtKt.isAfterDate(this, date);
    }

    @Override
    public boolean isBeforeDate(@NotNull BaseCalendar date) {
        return CalendarExtKt.isBeforeDate(this, date);
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

    @Override
    public int hashCode() {
        return this.persianYear * this.persianMonth * this.persianDay;
    }

    @Override
    public void set(int field, int value) {
        //Log.v("selected:",field+"  -  "+value);
        super.set(field, value);
        //  calculatePersianDate();
    }

    @NotNull
    @Override
    public BaseCalendar clone() {
        return createNewInstance(getTimeInMillis());
    }

    @Override
    public void setTimeInMillis(long millis) {
        super.setTimeInMillis(millis);
        calculateDate();
    }

    @Override
    public void setTimeZone(TimeZone zone) {
        super.setTimeZone(zone);
        calculateDate();
    }

    /**
     * add specific amout of fields to the current date for now doesnt handle
     * before 1 farvardin hejri (before epoch)
     *
     * @param field
     * @param amount u can also use Calendar.HOUR_OF_DAY,Calendar.MINUTE, Calendar.SECOND, Calendar.MILLISECOND etc
     */
    public void addPersianDate(int field, int amount) {
        if (amount == 0) {
            return; // Do nothing!
        }

        if (field < 0 || field >= ZONE_OFFSET) {
            throw new IllegalArgumentException();
        }

        if (field == YEAR) {
            setDate(this.persianYear + amount, getMonth(),
                    this.persianDay);
            return;
        } else if (field == MONTH) {
            setDate(this.persianYear
                            + ((getMonth() + amount) / 12),
                    (getMonth() + amount) % 12, this.persianDay);
            return;
        } else if (field == DAY_OF_MONTH) {
            setDate(getYear(), getMonth(),
                    this.persianDay + amount);
            return;
        }
        add(field, amount);
        calculateDate();
    }

    /**
     * @return String Name of the day in week
     */
    @NotNull
    public String getWeekDayNameShortType() {
        return getWeekNameInfo().getDayShortNameBy(getDayOfWeek());
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
        return "persian";
    }
}

