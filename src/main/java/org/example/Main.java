package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.json.simple.*;
import org.json.simple.parser.*;

import java.io.*;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException, ParseException {
        String credentialFilePath = args[0];
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(credentialFilePath));
        JSONObject jsonObject = (JSONObject) obj;
        String username = (String) jsonObject.get("username");
        String password = (String) jsonObject.get("password");
        Long schedulePeriodMin = (Long) jsonObject.get("schedulePeriodMin");
        Long loginWaitMillis = (Long) jsonObject.get("loginWaitMillis");
        Long adWaitMillis = (Long) jsonObject.get("adWaitMillis");
        Long afterAdQuitMillis = (Long) jsonObject.get("afterAdQuitMillis");
        Long adLoopCount = (Long) jsonObject.get("adLoopCount");

        ScheduledThreadPoolExecutor threadPool
                = new ScheduledThreadPoolExecutor(2);
        // WebDriver'ı başlat
        Runnable adWatcher = new AdWatcher(username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount);

        threadPool.scheduleAtFixedRate(adWatcher, 0, schedulePeriodMin, TimeUnit.MINUTES);
    }

    static class AdWatcher implements Runnable {
        private String username;
        private String password;
        private Long loginWaitMillis;
        private Long adWaitMillis;
        private Long afterAdQuitMillis;
        private Long adLoopCount;

        public AdWatcher(String username, String password, Long loginWaitMillis,
                         Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount) {
            this.username = username;
            this.password = password;
            this.loginWaitMillis = loginWaitMillis;
            this.adWaitMillis = adWaitMillis;
            this.afterAdQuitMillis = afterAdQuitMillis;
            this.adLoopCount = adLoopCount;
        }

        @Override
        public void run() {
            try {
                WebDriver driver = new ChromeDriver();
                watchAd(driver, username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private static void watchAd(WebDriver driver, String username, String password, Long loginWaitMillis,
                                    Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount) throws InterruptedException {
            driver.manage().window().maximize();

            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            driver.get("https://en.onlinesoccermanager.com/PrivacyNotice?nextUrl=%2FLogin");


            WebElement button = driver.findElement(By.cssSelector("#page-privacynotice > div > div > div:nth-child(2) > div:nth-child(3) > div > button"));
            button.click();

            WebElement loginButton = driver.findElement(By.cssSelector("#page-signup > div.page.content.hidden-before-binding > div.register-information-container.horizontal-center-absolute > div.register-information-block.buttons > button"));
            loginButton.click();


            WebElement usernameField = driver.findElement(By.cssSelector("#manager-name"));
            WebElement passwordField = driver.findElement(By.cssSelector("#password"));
            WebElement actualLoginButton = driver.findElement(By.cssSelector("#login"));
            usernameField.sendKeys(username);
            passwordField.sendKeys(password);
            Thread.currentThread().sleep(loginWaitMillis);
            actualLoginButton.click();
            Thread.currentThread().sleep(loginWaitMillis);
            driver.navigate().to("https://en.onlinesoccermanager.com/ChooseLeague/");


            driver.navigate().to("https://en.onlinesoccermanager.com/BusinessClub");
            String originalHandle = driver.getWindowHandle();
            try {

                for (int i = 0; i < adLoopCount; i++) {
                    Thread.currentThread().sleep(adWaitMillis);
                    WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div > div.wallet-amount.pull-left.center > div > span"));
                    System.out.println("Mevcut Boss Coin : " + currentCoin.getText());
                    driver.switchTo().newWindow(WindowType.TAB);
                    driver.get("https://en.onlinesoccermanager.com/BusinessClub");
                    WebElement watchAdButton = driver.findElement(By.cssSelector("#body-content > div.row.row-h-sm-600.row-h-md-23.overflow-hidden.theme-stepover-0.businessclub-container > div.col-xs-12.col-h-xs-22.col-h-sm-20.businessclub-rows-container > div > div:nth-child(1) > div"));
                    watchAdButton.click();
                    System.out.println("Reklam başlatıldı.");
                    Thread.currentThread().sleep(adWaitMillis);
                }

            } catch (Exception e) {
                System.out.println("Reklam butonuna tıklanamadı: " + e.getMessage());
            }
            System.out.println("Reklamlar İzlendi " + afterAdQuitMillis + " ms Sonra Kapanıyor");
            Thread.currentThread().sleep(afterAdQuitMillis);
            for (String handle : driver.getWindowHandles()) {
                if (!handle.equals(originalHandle)) {
                    driver.switchTo().window(handle);
                    driver.close();
                }
            }

            driver.switchTo().window(originalHandle);
            driver.quit();
        }
    }


}