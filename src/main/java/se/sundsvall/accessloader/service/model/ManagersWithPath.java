package se.sundsvall.accessloader.service.model;

import generated.se.sundsvall.employee.Employeev2;
import java.util.List;

public record ManagersWithPath(String orgPath, List<Employeev2> managers) {
}
