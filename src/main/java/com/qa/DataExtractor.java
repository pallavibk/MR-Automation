package com.qa;

import com.qa.dto.*;
import com.qa.util.DriverUtil;
import org.codehaus.jackson.map.ObjectMapper;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class DataExtractor {
    private static WebDriverWait waitObj;
    private static WebDriver driver;

    public static void main(String[] args) {
        try {
            driver = createDriver();
            driver.manage().window().maximize();
            driver.get("https://martianlogic.com/login/");

            driver.findElement(By.id("email")).click();
            driver.findElement(By.id("email")).sendKeys("poojitha.penumacha@awone.ai");
            driver.findElement(By.id("password")).click();
            driver.findElement(By.id("password")).sendKeys("Myrecruitment123");
            driver.findElement(By.xpath("//button[@type='submit']")).click();

            // Wait till the logon happens
            waitObj = new WebDriverWait(driver, 60);
            waitObj.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='x-treelist-item-text'][contains(string(),'Candidates')]")));

            //click on candidate
            driver.findElement(By.xpath("//div[@class='x-treelist-item-text'][contains(string(),'Candidates')]")).click();

            //click on candidate search
            waitObj.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='x-treelist-item-text'][contains(string(),'Candidate search')]")));
            driver.findElement(By.xpath("//div[@class='x-treelist-item-text'][contains(string(),'Candidate search')]")).click();

            //get the total number of page elements
            waitObj.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@id='totalCountOfApplicationsId-innerCt']")));
            String total_item = driver.findElement(By.xpath("//div[contains(@class, 'x-toolbar-text')][last()]")).getText();

            String[] numArr = total_item.split(" of ");
            int totalCount = Integer.parseInt(numArr[numArr.length - 1]);
            writeToJsonFile(generateCandidateList(totalCount));
        } catch (URISyntaxException e) {
            System.out.println("Failed to create the chrome driver");
            e.printStackTrace();
        } finally {
            driver.close();
        }
    }

    private static void writeToJsonFile(List<Candidate> candidateList) {
        ObjectMapper mapper = new ObjectMapper();
        File outputFile = new File("output.json");
        if (outputFile.exists()) {
            outputFile.delete();
        }
        try {
            mapper.writeValue(outputFile, candidateList);
        } catch (IOException e) {
            System.out.println("Failed to write to file.");
            e.printStackTrace();
        }
    }

    // Method generates the list of candidate objects
    private static List<Candidate> generateCandidateList(int totalCount) {
        List<Candidate> retList = new ArrayList<>(totalCount);
        int currCount = 0;
        Integer currPageNo = 1;
        while (true) {
            List<WebElement> tableElements = driver.findElements(By.xpath("//div[@class='x-grid-item-container']/table"));
            for (int idx = 1; idx <= tableElements.size(); idx++) {
                Candidate currCandidate = new Candidate();
                // Extract title, company, experience, degree, job stages and history from the card itself
                List<WebElement> spanElements = driver.findElements(By.xpath("//div[@class='x-grid-item-container']/table[" + idx + "]//table//span[@class='gridRowDataBlock']"));
                assert spanElements.size() == 5;
                currCandidate.setTitle(spanElements.get(1).getText());
                currCandidate.setCompany(spanElements.get(2).getText());
                currCandidate.setExperience(spanElements.get(3).getText());
                currCandidate.setDegree(spanElements.get(4).getText());

                // extract the job stages and history
                currCandidate.setJobStages(driver.findElement(By.xpath("//div[@class='x-grid-item-container']/table[" + idx + "]//div[@class='history-wrapper-body']/p")).getText());
                List<WebElement> historyElements = driver.findElements(By.xpath("//div[@class='x-grid-item-container']/table[" + idx + "]//div[@class='history-wrapper-body']/span"));
                StringBuilder historyBldr = new StringBuilder();
                historyElements.forEach(ele -> historyBldr.append(ele.getText()).append(" "));
                currCandidate.setHistory(historyBldr.toString().trim());

                // Hydrate candidate object with data related to address, phone-numbers and onboarding
                hydrateCandidateObject(currCandidate, idx);

                retList.add(currCandidate);
                currCount++;
                System.out.println("Processed " + currCount + " Objects");
            }
            if (currCount == totalCount) {
                break;
            }
            // Click on next button and wait for the next page to load
            WebElement nextBtn = driver.findElement(By.xpath("//div[contains(@class, 'x-toolbar-separator')][2]/following-sibling::a"));
            nextBtn.click();
            System.out.println("Clicked on the next button");
            // Wait till the next page is loaded, check if the page number has changed
            WebElement currPageLabel = driver.findElement(By.xpath("//div[contains(@class, 'x-toolbar-text')][contains(string(), 'Page')]/following-sibling::div/label"));
            String currPageFieldId = currPageLabel.getAttribute("for");
            WebElement currPageField = driver.findElement(By.id(currPageFieldId));
            System.out.println("Waiting for next page to load");
            waitObj.until(ExpectedConditions.attributeToBe(currPageField, "value", (++currPageNo).toString()));
            System.out.println("Next Page loaded");
        }
        return retList;
    }

    private static void hydrateCandidateObject(Candidate candidateObj, int idx) {
        // Click on the candidate name link to open the detailed view
        waitObj.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@class='x-grid-item-container']/table[" + idx + "]//div[@class='nameText']/a")));
        retryingElementClick("//div[@class='x-grid-item-container']/table[" + idx + "]//div[@class='nameText']/a");
        waitObj.until(ExpectedConditions.elementToBeClickable(By.xpath("//span[text()='My Fields']/ancestor::a")));

        retryingElementClick("//span[text()='My Fields']/ancestor::a");

        waitObj.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//font[contains(text(),'Name')]")));

        candidateObj.setName(extractInputFieldValue("Name"));
        candidateObj.setSurname(extractInputFieldValue("Surname"));
        candidateObj.setEmail(extractInputFieldValue("Email"));

        // Set the address object
        candidateObj.setAddress(generateAddressObj());

        // Set the Phone Number objects
        candidateObj.setMobilePhone(generatePhoneObj(PhoneType.MOBILE));
        candidateObj.setWorkPhone(generatePhoneObj(PhoneType.WORK));
        candidateObj.setHomePhone(generatePhoneObj(PhoneType.HOME));

        // Populate the On-boarding data
        candidateObj.setPacks(getOnboardingData());

        driver.findElement(By.xpath("//a[contains(@class, 'new-close-btn')]")).click();
    }

    private static List<Pack> getOnboardingData() {
        waitObj.until(ExpectedConditions.elementToBeClickable(By.xpath("//span[text()='Onboard']/ancestor::a")));
        retryingElementClick("//span[text()='Onboard']/ancestor::a");
        waitObj.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(text(),'START ONBOARDING')]")));
        // Find out if the list of onboarding docs is present
        List<WebElement> packElements = driver.findElements(By.xpath("(//span[@class='ro-listing-grid'])"));
        if (packElements == null || packElements.isEmpty()) {
            return null;
        }
        List<Pack> retList = new ArrayList<>(packElements.size());
        for (int idx = 1; idx <= packElements.size(); idx++) {
            Pack pack = new Pack();
            pack.setStatus(driver.findElement(By.xpath("(//span[@class='ro-listing-grid'])["+idx+"]/strong/b")).getText());
            pack.setProgress(driver.findElement(By.xpath("(//span[@class='ro-listing-grid'])["+idx+"]/strong/strong")).getText().split("\\|")[1].trim());
            pack.setName(driver.findElement(By.xpath("((//span[@class='ro-listing-grid'])["+idx+"]//strong[@class='ro-pack-info-ui'])[1]")).getText());
            pack.setCreatedDate(driver.findElement(By.xpath("((//span[@class='ro-listing-grid'])["+idx+"]//strong[@class='ro-pack-info-ui'])[2]")).getText());
            pack.setPosition(driver.findElement(By.xpath("((//span[@class='ro-listing-grid'])["+idx+"]//strong[@class='ro-pack-info-ui'])[3]")).getText());

            // Populate On-Boarding Docs data
            WebElement expanderSibling = driver.findElement(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]/following-sibling::div"));
            if (expanderSibling.getAttribute("class").contains("display--none-parent")) {
                // Scroll to the expander element
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", driver.findElement(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]")));
                // The pack card is not expanded yet. Hence, expand it
                retryingElementClick("(//div[contains(@class, 'x-tree-expander')])["+idx+"]");
                // Wait till the card has expanded
                waitObj.until(ExpectedConditions.presenceOfElementLocated(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]/following-sibling::div[contains(@class, 'display--none-parent-expanded')]")));
                // Wait till the list of docs appears
                waitObj.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]/ancestor::table/following-sibling::table[1]//div[@class='documentDescriptionGridCls']")));
                // Get list of web elements to find no of configured docs
                List<WebElement> docElements = driver.findElements(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]/ancestor::table/following-sibling::table//div[@class='documentDescriptionGridCls']"));
                List<String> docs = new ArrayList<>(docElements.size());
                for (int docIdx = 1; docIdx <= docElements.size(); docIdx++) {
                    docs.add(driver.findElement(By.xpath("(//div[contains(@class, 'x-tree-expander')])["+idx+"]/ancestor::table/following-sibling::table["+docIdx+"]//div[@class='documentDescriptionGridCls']")).getText());
                }
                pack.setDocs(docs);
                // Close the expanded card again
                retryingElementClick("(//div[contains(@class, 'x-tree-expander')])["+idx+"]");
            }
            retList.add(pack);
        }
        return retList;
    }

    private static void retryingElementClick(String xPath) {
        int attempts = 0;
        while(attempts < 3) {
            try {
                driver.findElement(By.xpath(xPath)).click();
                break;
            } catch (StaleElementReferenceException ex) {
                System.out.println("Stale Element Exception Occurred: " + ex.getMessage());
            } catch(Exception e) {
                System.out.println("Some Other Exception Occurred: " + e.getMessage());
            }
            System.out.println("Failed clicking on element. Retrying...");
            attempts++;
        }
    }

    private static String extractInputFieldValue(String labelName) {
        return driver.findElement(By.xpath("//input[@id='"+ getElementIdFromLabel(labelName) +"']")).getAttribute("value");
    }

    private static String getElementIdFromLabel(String labelName) {
        WebElement nameLabel = driver.findElement(By.xpath("//font[contains(text(),'"+ labelName +"')]/ancestor::label"));
        return nameLabel.getAttribute("for");
    }

    private static Address generateAddressObj() {
        Address addr = new Address();
        addr.setLocation(driver.findElement(By.xpath("//div[contains(@class, 'candidate-profile-area')]//img[contains(@src, 'location')]/following-sibling::p")).getText());
        addr.setStreet(extractInputFieldValue("Street"));
        addr.setTown(extractInputFieldValue("Town"));
        addr.setState(extractInputFieldValue("State"));
        addr.setCountry(extractInputFieldValue("Country"));
        addr.setPostcode(extractInputFieldValue("Postcode"));

        return addr;
    }

    private static Phone generatePhoneObj(PhoneType type) {
        String inputFieldId = getElementIdFromLabel(type.toString() + " phone");
        String phoneNum = driver.findElement(By.id(inputFieldId)).getAttribute("value");
        if (phoneNum.isBlank() || phoneNum.isEmpty()) {
            return null;
        }
        Phone phone = new Phone();
        // Get the country code
        WebElement flagEle = driver.findElement(By.xpath("//input[@id='" + inputFieldId + "']/preceding-sibling::div[@class='iti__flag-container']/div[@class='iti__selected-flag']"));
        phone.setCountryCode(flagEle.getAttribute("title"));
        phone.setNumber(phoneNum);

        return phone;
    }

    private static WebDriver createDriver() throws URISyntaxException {
        WebDriver driver = null;
//        String osname = System.getProperty("os.name");
//        if (osname.contains("Windows")) {
//            System.setProperty("webdriver.chrome.driver", "/Users/pallavik/MR+/src/main/resources/chromedriver-mac-aarch64.exe");
//            driver = new ChromeDriver();
//        } else {
//            System.setProperty("webdriver.chrome.driver", "/Users/pallavik/MR+/src/main/resources/chromedriver-mac-aarch64");
////                    ChromeOptions options = new ChromeOptions();
////                    options.setBinary("/usr/bin/chromium");
////                    options.addArguments("--no-sandbox", "--headless", "--disabled-gpu", "--ignore-certificate-errors");
////                    options.addArguments("start-maximized");
////                    options.addArguments("--disable-browser-side-navigation");
////                    options.addArguments("enable-automation");
////                    options.addArguments("window-size=1280,720");
////                    options.addArguments("--whitelisted-ips");
////                    options.addArguments("--disabled-dev-shm-usage");
////                    driver = new ChromeDriver(options);
//            driver = new ChromeDriver();
//        }
        System.setProperty("webdriver.chrome.driver", DriverUtil.getDriverPath());
        driver = new ChromeDriver();
        return driver;
    }
}
