// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;
import oracle.weblogic.kubernetes.actions.impl.primitive.WITParams;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.extensions.Timing;
import oracle.weblogic.kubernetes.extensions.tags.Slow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.assertions.TestAssertions;

@DisplayName("Simple validation of basic WIT functions")
class ItWITValidation implements LoggedTest {
    @Test
    @DisplayName("Create a MII image")
    @Slow
    public void testCreatingMIIImage() {

    	// install WIT using the default version and location
        boolean downloadWIT = TestActions.installWIT(
        		 null /* version */, 
        		 null /* location */
        		 false /* do not redirect output */);
        assertEquals(true, downloadWIT);
        
    	// install WDT using the default version and location
        boolean downloadWDT = TestActions.installWDT(
        		 null /* version */, 
        		 null /* location */
        		 false /* do not redirect output */);
        assertEquals(true, downloadWDT);

        // create the MII image
        // TODO add model files and other contents to the image once we have those resources
        WITParams params = new WITParams()
            .baseImageName(WebLogicImageTool.WLS_BASE_IMAGE_NAME)
            .baseImageTag(WebLogicImageTool.BASE_IMAGE_TAG)
            .modelImageName(WebLogicImageTool.MODEL_IMAGE_NAME)
            .modelImageTag(WebLogicImageTool.MODEL_IMAGE_TAG)
            .domainType(WebLogicImageTool.WLS);

        boolean success = TestActions.createMIIImage(params);
        assertEquals(true, success);
    }

}
