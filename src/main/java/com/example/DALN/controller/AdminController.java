
package com.example.DALN.controller;

import com.example.DALN.model.Booking;
import com.example.DALN.model.Court;
import com.example.DALN.model.OwnerRevenueReport;
import com.example.DALN.model.Settlement;
import com.example.DALN.model.User;

import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SettlementRepository;
import com.example.DALN.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final SettlementRepository settlementRepository;

    public AdminController(
            UserRepository userRepository,
            CourtRepository courtRepository,
            BookingRepository bookingRepository,
            SettlementRepository settlementRepository) {

        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.bookingRepository = bookingRepository;
        this.settlementRepository = settlementRepository;
    }

    // 1. DASHBOARD
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {

        Double revenue = bookingRepository.calculateTotalRevenue();
        LocalDate today = LocalDate.now();

        model.addAttribute(
                "totalRevenue",
                revenue != null ? revenue : 0.0
        );

        model.addAttribute(
                "todayBookingsCount",
                bookingRepository.countByCreatedAtBetween(
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                )
        );

        model.addAttribute(
                "totalCourtsCount",
                courtRepository.count()
        );

        model.addAttribute(
                "totalUsersCount",
                userRepository.count()
        );

        model.addAttribute(
                "latestBookings",
                bookingRepository.findTop10ByOrderByCreatedAtDesc()
        );

        model.addAttribute("activePage", "dashboard");

        return "admin/dashboard";
    }

    // 2. QUẢN LÝ SÂN BÃI
    @GetMapping("/courts")
    public String courtsManagement(Model model) {

        model.addAttribute("courts", courtRepository.findAll());
        model.addAttribute("activePage", "courts");

        return "admin/courts";
    }

    @GetMapping("/courts/new")
    public String newCourtForm(Model model) {

        model.addAttribute("court", new Court());
        model.addAttribute("activePage", "courts");

        return "admin/court-form";
    }

    @GetMapping("/courts/edit/{id}")
    public String editCourtForm(
            @PathVariable Long id,
            Model model) {

        Court court = courtRepository.findById(id).orElse(null);

        if (court == null) {
            return "redirect:/admin/courts?error=notfound";
        }

        model.addAttribute("court", court);
        model.addAttribute("activePage", "courts");

        return "admin/court-form";
    }

    @PostMapping("/courts/save")
    public String saveCourt(
            @ModelAttribute("court") Court court,
            Model model) {

        if (court.getId() == null) {
            courtRepository.save(court);
            return "redirect:/admin/courts?success=created";
        }

        Court existing = courtRepository.findById(court.getId())
                .orElse(null);

        if (existing == null) {
            model.addAttribute("court", court);
            model.addAttribute("activePage", "courts");
            model.addAttribute(
                    "error",
                    "Không tìm thấy sân cần cập nhật."
            );

            return "admin/court-form";
        }

        existing.setName(court.getName());
        existing.setSportType(court.getSportType());
        existing.setAddress(court.getAddress());
        existing.setOpenHours(court.getOpenHours());
        existing.setPricePerHour(court.getPricePerHour());
        existing.setImageUrl(court.getImageUrl());
        existing.setDescription(court.getDescription());
        existing.setAmenities(court.getAmenities());

        courtRepository.save(existing);

        return "redirect:/admin/courts?success=updated";
    }

    // XÓA SÂN
    @PostMapping("/courts/delete/{id}")
    public String deleteCourt(@PathVariable Long id) {

        Court court = courtRepository.findById(id).orElse(null);

        if (court == null) {
            return "redirect:/admin/courts?error=notfound";
        }

        try {
            courtRepository.delete(court);
            courtRepository.flush();

            return "redirect:/admin/courts?success=deleted";

        } catch (DataIntegrityViolationException e) {
            return "redirect:/admin/courts?error=related";

        } catch (Exception e) {
            return "redirect:/admin/courts?error=delete";
        }
    }

    // 3. QUẢN LÝ ĐƠN ĐẶT SÂN
    @GetMapping("/bookings")
    public String bookingsManagement(Model model) {

        model.addAttribute(
                "bookings",
                bookingRepository.findAllByOrderByCreatedAtDesc()
        );

        model.addAttribute("activePage", "bookings");

        return "admin/bookings";
    }

    // XEM CHI TIẾT ĐƠN ĐẶT SÂN
    @GetMapping("/bookings/{id}")
    public String bookingDetail(
            @PathVariable Long id,
            Model model) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn đặt sân"
                ));

        model.addAttribute("booking", booking);
        model.addAttribute("activePage", "bookings");

        return "admin/booking-detail";
    }

    // XUẤT DANH SÁCH ĐƠN ĐẶT SÂN RA EXCEL
    @GetMapping("/bookings/export")
    public void exportBookings(HttpServletResponse response)
            throws IOException {

        List<Booking> bookings =
                bookingRepository.findAllByOrderByCreatedAtDesc();

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );

        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"danh-sach-dat-san.xlsx\""
        );

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Don dat san");

            String[] columns = {
                    "Ma don",
                    "Khach hang",
                    "So dien thoai",
                    "San the thao",
                    "Ngay dat",
                    "Thoi gian choi",
                    "Tong tien",
                    "Tien coc",
                    "Con lai",
                    "Trang thai thanh toan"
            };

            Row header = sheet.createRow(0);

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int rowIndex = 1;

            for (Booking booking : bookings) {

                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(
                        booking.getBookingCode() != null
                                ? booking.getBookingCode() : ""
                );

                row.createCell(1).setCellValue(
                        booking.getCustomerName() != null
                                ? booking.getCustomerName()
                                : (booking.getUser() != null
                                && booking.getUser().getFullName() != null
                                ? booking.getUser().getFullName() : "")
                );

                row.createCell(2).setCellValue(
                        booking.getCustomerPhone() != null
                                ? booking.getCustomerPhone()
                                : (booking.getUser() != null
                                && booking.getUser().getPhone() != null
                                ? booking.getUser().getPhone() : "")
                );

                row.createCell(3).setCellValue(
                        booking.getCourt() != null
                                && booking.getCourt().getName() != null
                                ? booking.getCourt().getName() : ""
                );

                row.createCell(4).setCellValue(
                        booking.getBookingDate() != null
                                ? booking.getBookingDate().toString() : ""
                );

                row.createCell(5).setCellValue(
                        booking.getTimeSlot() != null
                                ? booking.getTimeSlot() : ""
                );

                row.createCell(6).setCellValue(
                        booking.getTotalPrice() != null
                                ? booking.getTotalPrice() : 0.0
                );

                row.createCell(7).setCellValue(
                        booking.getDepositAmount() != null
                                ? booking.getDepositAmount() : 0.0
                );

                row.createCell(8).setCellValue(
                        booking.getRemainingAmount() != null
                                ? booking.getRemainingAmount() : 0.0
                );

                row.createCell(9).setCellValue(
                        booking.getPaymentStatus() != null
                                ? booking.getPaymentStatus() : ""
                );
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(response.getOutputStream());
            response.flushBuffer();
        }
    }

    // 4. DANH SÁCH CHỦ SÂN VÀ KHÁCH HÀNG
    @GetMapping("/users")
    public String usersManagement(
            @RequestParam(name = "tab", defaultValue = "owners")
            String tab,
            Model model) {

        if (!"owners".equals(tab) && !"customers".equals(tab)) {
            tab = "owners";
        }

        model.addAttribute("activeTab", tab);
        model.addAttribute(
                "owners",
                userRepository.findByRole("ROLE_OWNER")
        );
        model.addAttribute(
                "customers",
                userRepository.findByRole("ROLE_USER")
        );
        model.addAttribute("activePage", "users");

        return "admin/users";
    }

    // KHÓA / MỞ KHÓA TÀI KHOẢN
    @PostMapping("/users/{id}/toggle-status")
    public String toggleStatus(
            @PathVariable Long id,
            @RequestParam("tab") String tab) {

        if (!"owners".equals(tab) && !"customers".equals(tab)) {
            tab = "owners";
        }

        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return "redirect:/admin/users?tab=" + tab
                    + "&error=notfound";
        }

        if ("ROLE_ADMIN".equals(user.getRole())) {
            return "redirect:/admin/users?tab=" + tab
                    + "&error=forbidden";
        }

        if ("owners".equals(tab)
                && !"ROLE_OWNER".equals(user.getRole())) {
            return "redirect:/admin/users?tab=owners&error=invalidrole";
        }

        if ("customers".equals(tab)
                && !"ROLE_USER".equals(user.getRole())) {
            return "redirect:/admin/users?tab=customers&error=invalidrole";
        }

        String currentStatus = user.getStatus();

        if ("ACTIVE".equalsIgnoreCase(currentStatus)) {
            user.setStatus("LOCKED");
        } else {
            user.setStatus("ACTIVE");
        }

        userRepository.save(user);

        return "redirect:/admin/users?tab=" + tab
                + "&success=statusupdated";
    }

    // CẬP NHẬT HOA HỒNG CHỦ SÂN
    @PostMapping("/owners/{id}/update-commission")
    public String updateCommission(
            @PathVariable Long id,
            @RequestParam("commissionRate") Double rate) {

        if (rate == null || !Double.isFinite(rate)
                || rate < 0 || rate > 100) {
            return "redirect:/admin/users?tab=owners&error=invalidcommission";
        }

        User owner = userRepository.findById(id).orElse(null);

        if (owner == null || !"ROLE_OWNER".equals(owner.getRole())) {
            return "redirect:/admin/users?tab=owners&error=notfound";
        }

        owner.setCommissionRate(rate);
        userRepository.save(owner);

        return "redirect:/admin/users?tab=owners&success=commissionupdated";
    }

    // 5. BÁO CÁO DOANH THU
    @GetMapping("/reports")
    public String reportsManagement(Model model) {

        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();
        String monthKey = currentMonth.toString();

        List<Booking> bookings =
                bookingRepository.findByBookingDateBetweenAndPaymentStatusIn(
                        startDate,
                        endDate,
                        List.of("PAID_DEPOSIT", "COMPLETED")
                );

        Map<Long, OwnerRevenueReport> reportMap = new HashMap<>();

        for (Booking booking : bookings) {

            Court court = booking.getCourt();

            if (court == null || court.getOwner() == null) {
                continue;
            }

            User owner = court.getOwner();

            OwnerRevenueReport report = reportMap.computeIfAbsent(
                    owner.getId(),
                    id -> new OwnerRevenueReport(owner)
            );

            double amount = booking.getTotalPrice() == null
                    ? 0.0
                    : booking.getTotalPrice();

            report.setGrossRevenue(
                    report.getGrossRevenue() + amount
            );
        }

        double totalGross = 0.0;
        double totalCommission = 0.0;
        double totalPayout = 0.0;

        for (OwnerRevenueReport report : reportMap.values()) {

            User owner = report.getOwner();

            double rate = owner.getCommissionRate() == null
                    ? 8.0
                    : owner.getCommissionRate();

            double commission =
                    report.getGrossRevenue() * rate / 100.0;

            double payout =
                    report.getGrossRevenue() - commission;

            report.setCommissionAmount(commission);
            report.setPayoutAmount(payout);

            Settlement settlement = settlementRepository
                    .findByOwnerIdAndSettlementMonth(
                            owner.getId(),
                            monthKey
                    )
                    .orElse(null);

            if (settlement != null) {
                report.setTransferCreated(true);
                report.setSettlementId(settlement.getId());
                report.setSettlementStatus(settlement.getStatus());
            }

            totalGross += report.getGrossRevenue();
            totalCommission += commission;
            totalPayout += payout;
        }

        List<OwnerRevenueReport> ownerReports =
                new ArrayList<>(reportMap.values());

        ownerReports.sort(
                Comparator.comparing(
                        OwnerRevenueReport::getGrossRevenue,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
        );

        model.addAttribute("ownerReports", ownerReports);
        model.addAttribute("totalGross", totalGross);
        model.addAttribute("totalCommission", totalCommission);
        model.addAttribute("totalPayout", totalPayout);
        model.addAttribute("reportMonth", monthKey);
        model.addAttribute("activePage", "reports");

        return "admin/reports";
    }

    // TẠO LỆNH QUYẾT TOÁN CHO CHỦ SÂN
    @PostMapping("/reports/settlements/{ownerId}/create")
    @Transactional
    public String createSettlement(
            @PathVariable Long ownerId,
            RedirectAttributes redirectAttributes) {

        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();
        String monthKey = currentMonth.toString();

        if (settlementRepository.existsByOwnerIdAndSettlementMonth(
                ownerId, monthKey)) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chủ sân đã có lệnh quyết toán trong tháng này."
            );

            return "redirect:/admin/reports";
        }

        User owner = userRepository.findById(ownerId).orElse(null);

        if (owner == null || !"ROLE_OWNER".equals(owner.getRole())) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Không tìm thấy tài khoản chủ sân hợp lệ."
            );

            return "redirect:/admin/reports";
        }

        List<Booking> bookings =
                bookingRepository.findByBookingDateBetweenAndPaymentStatusIn(
                        startDate,
                        endDate,
                        List.of("PAID_DEPOSIT", "COMPLETED")
                );

        double gross = 0.0;

        for (Booking booking : bookings) {

            Court court = booking.getCourt();

            if (court == null || court.getOwner() == null) {
                continue;
            }

            if (ownerId.equals(court.getOwner().getId())) {
                gross += booking.getTotalPrice() == null
                        ? 0.0
                        : booking.getTotalPrice();
            }
        }

        if (gross <= 0.0) {

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Chủ sân chưa có doanh thu hợp lệ để quyết toán."
            );

            return "redirect:/admin/reports";
        }

        double rate = owner.getCommissionRate() == null
                ? 8.0
                : owner.getCommissionRate();

        double commission = gross * rate / 100.0;
        double payout = gross - commission;

        Settlement settlement = Settlement.builder()
                .owner(owner)
                .settlementMonth(monthKey)
                .grossRevenue(gross)
                .commissionAmount(commission)
                .payoutAmount(payout)
                .bankName(owner.getBankName())
                .bankAccountNumber(owner.getBankAccountNumber())
                .bankAccountName(owner.getBankAccountName())
                .status("TRANSFER_CREATED")
                .build();

        settlementRepository.save(settlement);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Tạo lệnh quyết toán thành công cho "
                        + owner.getUsername() + "."
        );

        return "redirect:/admin/reports";
    }

    // 6. CÀI ĐẶT HỆ THỐNG
    @GetMapping("/settings")
    public String settingsManagement(Model model) {

        model.addAttribute("activePage", "settings");

        return "admin/settings";
    }
}
