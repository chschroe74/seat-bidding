package de.gigaworks.seatbidding.persistence;

import de.gigaworks.seatbidding.notification.ReminderStartWeekday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_notification_settings")
public class EmployeeNotificationSettingsEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    public EmployeeEntity employee;

    @Column(name = "bid_reminders_enabled", nullable = false)
    public boolean bidRemindersEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "bid_reminder_start_weekday", nullable = false, length = 16)
    public ReminderStartWeekday bidReminderStartWeekday = ReminderStartWeekday.MONDAY;

}