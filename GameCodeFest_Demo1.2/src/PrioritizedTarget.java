import jsclub.codefest.sdk.base.Node;

/**
 * Một lớp dữ liệu đơn giản để chứa một mục tiêu tiềm năng (vật phẩm, rương)
 * và độ dài đường đi đã tính toán để đến đó.
 * Điều này cho phép HeroController dễ dàng so sánh các loại mục tiêu khác nhau
 * và chọn mục tiêu hiệu quả nhất.
 */
public record PrioritizedTarget(Node target, int pathLength) {
    // Record này không chứa logic bổ sung.
}
