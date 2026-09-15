package se.sundsvall.accessloader.service;

import generated.se.sundsvall.mdviewer.MDVEmployee;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import se.sundsvall.accessloader.integration.mdviewer.MdViewerClient;

@Service
public class MdViewerService {

	private final MdViewerClient mdViewerClient;

	MdViewerService(final MdViewerClient mdViewerClient) {
		this.mdViewerClient = mdViewerClient;
	}

	public OrganizationTree getOrgTree(final int orgId) {
		return mdViewerClient.getOrgTree(orgId, null);
	}

	public List<String> getPersonIds(final int orgId) {
		return mdViewerClient.getEmployees(orgId).stream()
			.map(MDVEmployee::getPersonId)
			.filter(Objects::nonNull)
			.map(UUID::toString)
			.toList();
	}
}
