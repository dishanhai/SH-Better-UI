// Author: dishanhai
// Call from a client-side event or key binding after SHUI is available.
global.openExampleRichText = () => {
  const rich = SHUI.richText()
  const draft = rich.markup('kubejs:diamond_guide',
    '[b][tr=item.minecraft.diamond;Diamond][/b] [img=minecraft:textures/item/diamond.png;16;16;]')
  rich.open(rich.json(rich.encode(draft)))
}
