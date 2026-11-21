// Visual proof that the two derived ink-layer fonts register exactly.
//
// Run from `mobile/android/`:
//     swift tools/render-font-layers.swift [output.png]
// Default output: $TMPDIR/hawraa-font-layers.png
//
// Each pair of rows shows (1) the source face in one colour, then (2) the same line
// drawn as marks-only (gold) + letters-only (ink). If the pair looks identical apart
// from the diacritic colour, the split is correct.

import Foundation
import CoreGraphics
import CoreText
import ImageIO
import UniformTypeIdentifiers
import AppKit

let root = FileManager.default.currentDirectoryPath
func fontPath(_ relative: String) -> String { root + "/" + relative }

func loadFont(_ path: String, _ size: CGFloat) -> CTFont? {
    guard let url = URL(string: "file://" + path) as CFURL?,
          let provider = CGDataProvider(url: url),
          let cgFont = CGFont(provider) else { return nil }
    return CTFontCreateWithGraphicsFont(cgFont, size, nil, nil)
}

func drawLine(_ ctx: CGContext, _ text: String, _ font: CTFont, color: CGColor, x: CGFloat, y: CGFloat) {
    let attrs: [NSAttributedString.Key: Any] = [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color,
    ]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
    ctx.textPosition = CGPoint(x: x, y: y)
    CTLineDraw(line, ctx)
}

let orig = fontPath("tools/fonts/scheherazade-new-regular.ttf")
let letters = fontPath("app/src/main/res/font/quran_letters.ttf")
let marks = fontPath("app/src/main/res/font/quran_marks.ttf")

let lines = [
    "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
    "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
    "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
]
let size: CGFloat = 46
let rowH: CGFloat = 110
let W: CGFloat = 1150
let H: CGFloat = rowH * CGFloat(lines.count * 2) + 30

let cs = CGColorSpaceCreateDeviceRGB()
guard let ctx = CGContext(data: nil, width: Int(W), height: Int(H), bitsPerComponent: 8,
                          bytesPerRow: 0, space: cs,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { exit(1) }
ctx.setFillColor(CGColor(red: 0.984, green: 0.965, blue: 0.925, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: W, height: H))

let ink = CGColor(red: 0.14, green: 0.12, blue: 0.09, alpha: 1)
let gold = CGColor(red: 0.66, green: 0.46, blue: 0.07, alpha: 1)

guard let fOrig = loadFont(orig, size),
      let fLetters = loadFont(letters, size),
      let fMarks = loadFont(marks, size) else {
    print("missing font — run from mobile/android/")
    exit(1)
}

var y = H - 20
for text in lines {
    // reference: source face, single colour
    drawLine(ctx, text, fOrig, color: ink, x: 30, y: y - 70)
    y -= rowH
    // two layers: marks in gold under, letters in ink over
    drawLine(ctx, text, fMarks, color: gold, x: 30, y: y - 70)
    drawLine(ctx, text, fLetters, color: ink, x: 30, y: y - 70)
    y -= rowH
}

let outPath: String
if CommandLine.arguments.count > 1 {
    outPath = CommandLine.arguments[1]
} else {
    outPath = NSTemporaryDirectory() + "hawraa-font-layers.png"
}

guard let img = ctx.makeImage() else { exit(1) }
let outURL = URL(fileURLWithPath: outPath)
guard let dest = CGImageDestinationCreateWithURL(outURL as CFURL, UTType.png.identifier as CFString, 1, nil) else { exit(1) }
CGImageDestinationAddImage(dest, img, nil)
CGImageDestinationFinalize(dest)
print("wrote \(outURL.path)")
