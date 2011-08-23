package gov.noaa.pmel.tmap.catalogcleaner.data;

public class ServiceService extends Service {
	private int parentId;

	public int getParentId(){
		return this.parentId;
	}

	public int getChildId(){
		return this.serviceId;
	}

	public ServiceService(int parentId, int serviceId){
		super(serviceId);
		this.parentId = parentId;
	}
	public ServiceService(int parentId, int serviceId, String suffix, String name, String base, String desc, String servicetype, String status){
		super(serviceId, suffix, name, base, desc, servicetype, status);
		this.parentId = parentId;
	}
}
