package se.sundsvall.accessloader.service.model;

import generated.se.sundsvall.employee.Manager;
import java.util.List;

public record ManagersWithPath(String orgPath, List<Manager> managers) {
}
