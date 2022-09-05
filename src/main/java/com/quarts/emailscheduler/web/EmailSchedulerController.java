package com.quarts.emailscheduler.web;

import com.quarts.emailscheduler.payload.EmailRequest;
import com.quarts.emailscheduler.payload.EmailResponse;
import com.quarts.emailscheduler.quartz.job.EmailJob;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

@Slf4j
@RestController
public class EmailSchedulerController {

    @Autowired
    private Scheduler scheduler;

    // It represents an instance of Job. It also contains additional data in the form of a JobDataMap that
    // is passed to the job when it is executed
    // every JobDetails is identified by JobKey that consists of name and group


    @PostMapping(value = "/schedule/email", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmailResponse> scheduleEmail(@Valid @RequestBody EmailRequest emailRequest){
        try{
            ZonedDateTime dateTime = ZonedDateTime.of(emailRequest.getDateTime(), emailRequest.getTimeZone());
            if(dateTime.isBefore(ZonedDateTime.now())){
                EmailResponse emailResponse = new EmailResponse(false,
                        "Date Time must be after current time");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(emailResponse);
            }

            JobDetail jobDetail = buildJobDetail(emailRequest);
            Trigger trigger = buildTrigger(jobDetail, dateTime);

            scheduler.scheduleJob(jobDetail, trigger);

            EmailResponse emailResponse = new EmailResponse(true,
                    jobDetail.getKey().getName(), jobDetail.getKey().getGroup(),
                    "Email Scheduled Successfully!");

            return ResponseEntity.ok(emailResponse);

        }catch (SchedulerException se){
            log.error("Error while scheduling email: ", se);
            EmailResponse emailResponse = new EmailResponse(false,
                    "Error while scheduling email. Please try again!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(emailResponse);
        }
    }

    @GetMapping("/get")
    public ResponseEntity<String> getApiTest(){
        return ResponseEntity.ok("Get API Test - Pass");
    }


    private JobDetail buildJobDetail(EmailRequest scheduleEmailRequest){
        JobDataMap jobDataMap = new JobDataMap();

        jobDataMap.put("email", scheduleEmailRequest.getEmail());
        jobDataMap.put("subject", scheduleEmailRequest.getSubject());
        jobDataMap.put("body", scheduleEmailRequest.getBody());

        return JobBuilder.newJob(EmailJob.class)
                .withIdentity(UUID.randomUUID().toString(), "email-jobs")
                .withDescription("Send Email Job")
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    //trigger
    // it defines the schedule at which a given job will be executed.
    // Job can have many trigger, but a Trigger can only be one job
    private Trigger buildTrigger(JobDetail jobDetail, ZonedDateTime startAt) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobDetail.getKey().getName(), "email-triggers")
                .withDescription("Send Email Trigger")
                .startAt(Date.from(startAt.toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
    }
}
