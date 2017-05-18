/**
 * Copyright 2007-2016, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.gateway.server;

import org.junit.Test;
import org.kaazing.gateway.server.util.ProductInfo;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.kaazing.gateway.server.impl.ProductInfoReader.getProductInfoInstance;

public class SettingsIT {

    /**
     * Checks in the project's artifact that the manifest entries will be generated as expected by others (update.check).
     */
    @Test
    public void shouldHaveCommunityProductEditionAndTitle() throws IOException {
        ProductInfo productInfo = getProductInfoInstance();
        assumeTrue("Disabled in IDE", System.getProperty("java.class.path").contains("gateway.server"));
        assertEquals("Community.Gateway", productInfo.getEdition());
        assertEquals("Kaazing Gateway", productInfo.getTitle());
    }
}

