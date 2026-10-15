#if canImport(Testing)
import Testing
import Tokenizers

@Suite("Tokenizers Swift Export Tests")
struct TokenizersExportTests {
    @Test("Tokenizers swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        #expect(Bool(true), "Tokenizers swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Tokenizers

final class TokenizersExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Tokenizers swift module imported cleanly")
    }
}
#endif
