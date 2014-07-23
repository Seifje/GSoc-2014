package org.apache.cloudstack.api.command.admin.storage;


import org.apache.log4j.Logger;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandJobType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.BaseCmd.CommandType;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.cloudstack.api.response.StoragePoolResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import  org.apache.cloudstack.api.response.StorageTagResponse;

@APICommand(name = "listStorageTags", description = "Lists storage tags.", responseObject = StorageTagResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListStorageTagsCmd extends BaseListCmd {

	 public static final Logger s_logger = Logger.getLogger(ListStorageTagsCmd.class.getName());

	    private static final String s_name = "liststoragetagsresponse";

	    /////////////////////////////////////////////////////
	    //////////////// API parameters /////////////////////
	    /////////////////////////////////////////////////////


	    /////////////////////////////////////////////////////
	    /////////////////// Accessors ///////////////////////
	    /////////////////////////////////////////////////////

	
	    /////////////////////////////////////////////////////
	    /////////////// API Implementation///////////////////
	    /////////////////////////////////////////////////////

	    @Override
	    public String getCommandName() {
	        return s_name;
	    }

	    @Override
	    public ApiCommandJobType getInstanceType() {
	        return ApiCommandJobType.StoragePool;//to_change
	    }

	    @Override
	    public void execute() {
	        ListResponse<StorageTagResponse> response = _queryService.searchForStorageTags(this);//to_change
	        response.setResponseName(getCommandName());
	        this.setResponseObject(response);
	    }

	    
}
