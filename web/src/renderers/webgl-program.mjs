// Author: dishanhai
export function linkProgram(gl, label, vertex, fragment) {
  const program = gl.createProgram();
  if (!program) throw new Error(`无法分配 ${label} 程序`);
  const shaders = [];
  try {
    for (const [kind, source] of [[gl.VERTEX_SHADER, vertex], [gl.FRAGMENT_SHADER, fragment]]) {
      const shader = gl.createShader(kind);
      if (!shader) throw new Error(`无法分配 ${label} 着色器`);
      shaders.push(shader);
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        throw new Error(`${label} 着色器编译失败。${gl.getShaderInfoLog(shader)}`);
      }
      gl.attachShader(program, shader);
    }
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      throw new Error(`${label} 着色器链接失败。${gl.getProgramInfoLog(program)}`);
    }
    for (const shader of shaders) gl.detachShader(program, shader);
    return program;
  } catch (error) {
    gl.deleteProgram(program);
    throw error;
  } finally {
    for (const shader of shaders) gl.deleteShader(shader);
  }
}
