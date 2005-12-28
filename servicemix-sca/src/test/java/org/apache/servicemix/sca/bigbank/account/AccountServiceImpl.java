/*
 * Copyright 2005-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.sca.bigbank.account;

import org.apache.servicemix.sca.bigbank.accountdata.AccountDataService;
import org.apache.servicemix.sca.bigbank.stockquote.StockQuoteService;
import org.osoa.sca.annotations.Property;
import org.osoa.sca.annotations.Reference;

public class AccountServiceImpl implements AccountService {

    @Property
    public String currency = "USD";

    @Reference
    public AccountDataService accountDataService;
    @Reference
    public StockQuoteService stockQuoteService;

    public AccountServiceImpl() {
    }

    public AccountReport getAccountReport(String customerID) {
    	stockQuoteService.getQuote("IBM");
        return null;
    }

}
