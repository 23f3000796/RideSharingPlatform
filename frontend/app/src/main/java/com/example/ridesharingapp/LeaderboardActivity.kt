package com.example.ridesharingapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ridesharingapp.databinding.ActivityLeaderboardBinding
import com.example.ridesharingapp.databinding.ItemLeaderboardBinding
import com.example.ridesharingapp.databinding.ItemPodiumBinding
import com.example.ridesharingapp.network.ModelMapper
import kotlinx.coroutines.launch
import java.util.Locale

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)

        val me = RideRepository.currentUser
        binding.tvMyRank.text = String.format(Locale.getDefault(), "You have %,d points · %s", me.rewardPoints, me.levelTitle)

        lifecycleScope.launch {
            RideSharingRepository.getLeaderboard(page = 1, limit = 50).fold(
                onSuccess = { (rows, _) -> render(rows) },
                onFailure = { e ->
                    Toast.makeText(this@LeaderboardActivity, e.message ?: "Could not load leaderboard", Toast.LENGTH_LONG).show()
                    binding.rvLeaderboard.adapter = LeaderboardAdapter(emptyList(), me.userId)
                },
            )
            binding.progress.visibility = View.GONE
        }
    }

    private fun render(rows: List<LeaderboardUiRow>) {
        val myId = RideRepository.currentUserId
        rows.firstOrNull { it.userId == myId }?.let {
            binding.tvMyRank.text = String.format(Locale.getDefault(), "You're #%d with %,d points · %s", it.rank, it.points, it.levelTitle)
        }

        val podium = rows.take(3)
        if (podium.size == 3) {
            binding.layoutPodium.visibility = View.VISIBLE
            bindPodium(binding.podium1, podium[0], "🥇", myId)
            bindPodium(binding.podium2, podium[1], "🥈", myId)
            bindPodium(binding.podium3, podium[2], "🥉", myId)
            binding.rvLeaderboard.adapter = LeaderboardAdapter(rows.drop(3), myId)
        } else {
            binding.layoutPodium.visibility = View.GONE
            binding.rvLeaderboard.adapter = LeaderboardAdapter(rows, myId)
        }
    }

    private fun bindPodium(p: ItemPodiumBinding, row: LeaderboardUiRow, medal: String, myId: String) {
        p.tvMedal.text = medal
        p.tvAvatar.text = UiKit.initials(row.name)
        p.tvAvatar.background = UiKit.circle(ModelMapper.avatarColorFor(row.userId))
        p.tvName.text = if (row.userId == myId) "You" else shortName(row.name)
        p.tvPoints.text = String.format(Locale.getDefault(), "%,d pts", row.points)
    }

    private fun shortName(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return if (parts.size >= 2) "${parts[0]} ${parts[1].first()}." else name
    }

    class LeaderboardAdapter(private val users: List<LeaderboardUiRow>, private val myId: String) :
        RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val u = users[position]
            val b = holder.binding
            val ctx = b.root.context
            val isMe = u.userId == myId
            b.tvRank.text = u.rank.toString()
            b.tvAvatar.text = UiKit.initials(u.name)
            b.tvAvatar.background = UiKit.circle(ModelMapper.avatarColorFor(u.userId))
            b.tvName.text = if (isMe) "${u.name} (you)" else u.name
            b.tvSub.text = listOfNotNull(
                u.levelTitle,
                UiKit.plural(u.ridesCompleted, "trip"),
                u.rating.takeIf { it > 0f }?.let { "★${UiKit.rating(it)}" },
                u.department?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            b.tvPoints.text = String.format(Locale.getDefault(), "%,d pts", u.points)
            b.card.setCardBackgroundColor(ctx.getColor(if (isMe) R.color.info_bg else R.color.card_background))
            b.card.strokeColor = ctx.getColor(if (isMe) R.color.accent_blue else R.color.border_divider)
        }

        override fun getItemCount() = users.size

        class ViewHolder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
