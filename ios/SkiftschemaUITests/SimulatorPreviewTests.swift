import XCTest

final class SimulatorPreviewTests: XCTestCase {
    @MainActor
    func testOnboardingMonthAndWeek() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Välj schema"].waitForExistence(timeout: 15))
        capture("01-onboarding")

        app.buttons["6-skift"].tap()
        app.buttons["Fortsätt"].tap()
        app.buttons["Skift B"].tap()
        app.buttons["Fortsätt"].tap()
        app.buttons["Visa schemat"].tap()

        XCTAssertTrue(app.staticTexts["Nästa pass"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Månad"].isSelected)
        capture("02-month")

        app.buttons["Vecka"].tap()
        XCTAssertTrue(app.buttons["Vecka"].isSelected)
        capture("03-week")
    }

    @MainActor
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
