const { chromium } = require('C:/Users/admin.igor/AppData/Local/npm-cache/_npx/db89d7302a373f10/node_modules/playwright');
const fs = require('fs');
(async () => {
 const browser = await chromium.launch({headless:true});
 const page = await browser.newPage({viewport:{width:390,height:844},acceptDownloads:true});
 await page.goto('http://127.0.0.1:18765/photo/12345678-1234-1234-1234-123456789abc');
 await page.locator('img').evaluate(img => img.decode());
 if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)) throw Error('Horizontal overflow');
 const [download] = await Promise.all([page.waitForEvent('download'),page.getByText('Baixar minha foto',{exact:true}).click()]);
 await download.saveAs('reports/finalization/photo-probe/downloaded-photo.jpg');
 if(!fs.readFileSync('reports/finalization/photo-probe/downloaded-photo.jpg').equals(fs.readFileSync('reports/finalization/photo-probe/framed-photo.jpg'))) throw Error('File mismatch');
 await page.screenshot({path:'reports/finalization/photo-probe/mobile-download-page.png',fullPage:true});
 console.log('PASS: mobile page 390px, no overflow, image loaded, button downloads exact framed JPEG');
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
