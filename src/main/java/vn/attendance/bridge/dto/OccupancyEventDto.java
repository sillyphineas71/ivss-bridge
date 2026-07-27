package vn.attendance.bridge.dto;

import lombok.Data;

/**
 * Occupancy (People Counting / Number Stat) event pushed to NestJS capstone-be.
 * Nguồn: EVENT_IVS_NUMBERSTAT (0x10) -> struct DEV_EVENT_NUMBERSTAT_INFO.
 *
 * Hợp đồng B29:
 *   POST  ${nestjs.occupancy-webhook-url}
 *   header X-Internal-Token: <api-key>
 *   body : channelId + utc + number (bắt buộc); entered/exited/eventAction optional.
 *
 * BE dùng `number` làm headcount cho occupancy-snapshot (suy ra no-show);
 * entered/exited để dành cho hướng đếm vào/ra (C13/14) sau này.
 */
@Data
public class OccupancyEventDto {
    private String type = "occupancy";  // nhãn cố định, BE phân loại
    private int    channelId;           // IVSS channel -> map sang room ở BE
    private String utc;                 // ISO local time từ device
    private int    number;              // nNumber: số người HIỆN trong vùng (headcount)
    private int    enteredNumber;       // nEnteredNumber: luỹ kế vào
    private int    exitedNumber;        // nExitedNumber: luỹ kế ra
    private String eventAction;         // "pulse" | "start" | "end" (từ bEventAction 0/1/2)
}